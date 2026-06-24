package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import org.jetbrains.kotlin.psi.KtFile
import kotlin.experimental.or

/**
 * Renames project references to a string key when its declaration is renamed: Android
 * `R.string.old`/`@string/old` and Compose Multiplatform `Res.string.old`. The `strings.xml` files
 * themselves are renamed separately by the caller, so they are skipped here.
 */
object ReferenceRenamer {

    /** A rename computed by [planRename]: the reference offsets to rewrite, ready to apply under a write lock. */
    class RenamePlan(
        val oldKey: String,
        val newKey: String,
        val system: ResourceSystem,
        internal val edits: Map<Document, List<Int>>,
        internal val touchedKtFiles: List<KtFile>
    )

    /**
     * Searches for in-scope references and returns a [RenamePlan]. Run this OUTSIDE the write command:
     * `processElementsWithWord` is a potentially slow project search, and holding the write lock across
     * it blocks the EDT. The plan only records offsets, so it stays valid as long as the searched files
     * aren't edited before [applyPlan] (the caller only mutates `strings.xml`, which is skipped here).
     */
    fun planRename(project: Project, declaringFile: VirtualFile, oldKey: String, newKey: String, system: ResourceSystem): RenamePlan {
        // Code references (R.string. / Res.string.) are module-local, so scope to the declaring module and
        // its dependents — never the whole project, where another module's identically-named key would be
        // wrongly rewritten while its own strings.xml keeps the old name.
        val scope = ownerScope(project, declaringFile)
        val helper = PsiSearchHelper.getInstance(project)
        val docManager = PsiDocumentManager.getInstance(project)

        val edits = LinkedHashMap<Document, MutableSet<Int>>()
        val touchedKtFiles = LinkedHashSet<KtFile>()

        // Code prefixes only in code (IN_STRINGS would rewrite `R.string.old` sitting inside a literal/log).
        val codePrefix = when (system) {
            ResourceSystem.ANDROID -> ResourceSystem.ANDROID_REF_PREFIX
            ResourceSystem.COMPOSE_MULTIPLATFORM -> ResourceSystem.CMP_REF_PREFIX
        }
        collect(
            helper, scope, oldKey, listOf(codePrefix), docManager, edits, touchedKtFiles,
            (UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES)
        )
        // `@string/` lives in XML attribute values (Android only); those are indexed as foreign-language/strings.
        if (system == ResourceSystem.ANDROID) {
            collect(
                helper, scope, oldKey, listOf(ResourceSystem.XML_REF_PREFIX), docManager, edits, touchedKtFiles,
                (UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS)
            )
        }
        return RenamePlan(oldKey, newKey, system, edits.mapValues { it.value.toList() }, touchedKtFiles.toList())
    }

    /** Applies a [RenamePlan]'s edits (call inside a write command) and returns how many sites were rewritten. */
    fun applyPlan(project: Project, plan: RenamePlan): Int {
        val docManager = PsiDocumentManager.getInstance(project)
        var renamed = 0
        plan.edits.forEach { (doc, offsets) ->
            offsets.sortedDescending().forEach { start ->
                doc.replaceString(start, start + plan.oldKey.length, plan.newKey)
                renamed++
            }
            docManager.commitDocument(doc)
        }
        if (plan.system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            plan.touchedKtFiles.forEach { KtImportUtil.addCmpKeyImport(project, it, plan.newKey) }
        }
        return renamed
    }

    private fun ownerScope(project: Project, declaringFile: VirtualFile): SearchScope {
        val module = ModuleUtilCore.findModuleForFile(declaringFile, project)
        return if (module != null) {
            GlobalSearchScope.moduleWithDependentsScope(module)
        } else {
            GlobalSearchScope.projectScope(project)
        }
    }

    private fun collect(
        helper: PsiSearchHelper,
        scope: SearchScope,
        oldKey: String,
        prefixes: List<String>,
        docManager: PsiDocumentManager,
        edits: MutableMap<Document, MutableSet<Int>>,
        touchedKtFiles: MutableSet<KtFile>,
        searchContext: Short
    ) {
        helper.processElementsWithWord(
            { element, _ ->
                ProgressManager.checkCanceled()
                val file = element.containingFile ?: return@processElementsWithWord true
                if (file.name == "strings.xml") return@processElementsWithWord true
                val text = element.text ?: return@processElementsWithWord true
                val baseOffset = element.textRange.startOffset
                for (prefix in prefixes) {
                    val needle = prefix + oldKey
                    var idx = text.indexOf(needle)
                    while (idx >= 0) {
                        val after = text.getOrNull(idx + needle.length)
                        if (after == null || !(after.isLetterOrDigit() || after == '_')) {
                            val doc = docManager.getDocument(file)
                            if (doc != null) {
                                edits.getOrPut(doc) { sortedSetOf() }.add(baseOffset + idx + prefix.length)
                                (file as? KtFile)?.let { touchedKtFiles += it }
                            }
                        }
                        idx = text.indexOf(needle, idx + 1)
                    }
                }
                true
            },
            scope,
            oldKey,
            searchContext,
            true
        )
    }

}
