package com.seijind.stringsmith.extract

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import kotlin.experimental.or

/**
 * Renames project references to a string key when its declaration is renamed: Android
 * `R.string.old`/`@string/old` and Compose Multiplatform `Res.string.old`. The `<string name="old">`
 * declarations themselves are renamed separately by the caller.
 */
object ReferenceRenamer {

    /** A rename computed by [planRename]: the key ranges to rewrite, ready to apply under a write lock. */
    class RenamePlan(
        val oldKey: String,
        val newKey: String,
        val system: ResourceSystem,
        internal val edits: Map<Document, List<RangeMarker>>,
        internal val cmpResPackages: Map<KtFile, String>
    )

    /**
     * Searches for in-scope references and returns a [RenamePlan]. Run this OUTSIDE the write command:
     * `processElementsWithWord` is a potentially slow project search, and holding the write lock across
     * it blocks the EDT. Range markers keep the plan valid while the caller edits the `strings.xml` files
     * (which may hold `@string/old` aliases) before [applyPlan].
     */
    fun planRename(project: Project, declaringFile: VirtualFile, oldKey: String, newKey: String, system: ResourceSystem): RenamePlan {
        // Code references (R.string. / Res.string.) are module-local, so scope to the declaring module and
        // its dependents, never the whole project, where another module's identically-named key would be
        // wrongly rewritten while its own strings.xml keeps the old name.
        val scope = ownerScope(project, declaringFile)
        val docManager = PsiDocumentManager.getInstance(project)

        val edits = LinkedHashMap<Document, MutableList<RangeMarker>>()
        val touchedKtFiles = LinkedHashSet<KtFile>()
        val collect = { file: PsiFile, keyStart: Int ->
            docManager.getDocument(file)?.let { doc ->
                edits.getOrPut(doc) { mutableListOf() } += doc.createRangeMarker(keyStart, keyStart + oldKey.length)
                (file as? KtFile)?.let { touchedKtFiles += it }
            }
            true
        }

        val codePrefix = when (system) {
            ResourceSystem.ANDROID -> ResourceSystem.ANDROID_REF_PREFIX
            ResourceSystem.COMPOSE_MULTIPLATFORM -> ResourceSystem.CMP_REF_PREFIX
        }
        // Kotlin indexes string contents as foreign language, so the search context alone doesn't keep
        // `"R.string.old"` in a log message or comment out.
        KeyReferences.forEach(
            project, scope, oldKey, listOf(codePrefix),
            UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES
        ) { file, keyStart -> isInCommentOrString(file, keyStart) || collect(file, keyStart) }
        // `@string/` lives in XML attribute values and values (Android only); those are indexed as foreign-language/strings.
        if (system == ResourceSystem.ANDROID) {
            KeyReferences.forEach(
                project, scope, oldKey, listOf(ResourceSystem.XML_REF_PREFIX),
                UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS, collect
            )
        }
        val cmpResPackages = if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            touchedKtFiles.mapNotNull { file -> KtImportUtil.cmpResPackage(file)?.let { file to it } }.toMap()
        } else emptyMap()
        return RenamePlan(oldKey, newKey, system, edits, cmpResPackages)
    }

    /** Applies a [RenamePlan]'s edits (call inside a write command) and returns how many sites were rewritten. */
    fun applyPlan(project: Project, plan: RenamePlan): Int {
        val docManager = PsiDocumentManager.getInstance(project)
        var renamed = 0
        plan.edits.forEach { (doc, markers) ->
            markers.sortedByDescending { it.startOffset }.forEach { marker ->
                if (marker.isValid && marker.endOffset - marker.startOffset == plan.oldKey.length) {
                    doc.replaceString(marker.startOffset, marker.endOffset, plan.newKey)
                    renamed++
                }
                marker.dispose()
            }
            docManager.commitDocument(doc)
        }
        plan.cmpResPackages.forEach { (file, resPackage) -> KtImportUtil.addCmpKeyImport(file, resPackage, plan.newKey) }
        return renamed
    }

    private fun isInCommentOrString(file: PsiFile, offset: Int): Boolean {
        val leaf = file.findElementAt(offset) ?: return false
        if (PsiTreeUtil.getParentOfType(leaf, PsiComment::class.java, false) != null) return true
        val stringTokens = LanguageParserDefinitions.INSTANCE.forLanguage(leaf.language)?.stringLiteralElements ?: return false
        return leaf.node.elementType in stringTokens
    }

    private fun ownerScope(project: Project, declaringFile: VirtualFile): SearchScope {
        val module = ModuleUtilCore.findModuleForFile(declaringFile, project)
        return if (module != null) {
            GlobalSearchScope.moduleWithDependentsScope(module)
        } else {
            GlobalSearchScope.projectScope(project)
        }
    }
}
