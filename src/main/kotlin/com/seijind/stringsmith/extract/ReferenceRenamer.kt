package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtilCore
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

    fun rename(project: Project, declaringFile: VirtualFile, oldKey: String, newKey: String, system: ResourceSystem) {
        // Code references (R.string. / Res.string.) are module-local, so scope to the declaring module and
        // its dependents — never the whole project, where another module's identically-named key would be
        // wrongly rewritten while its own strings.xml keeps the old name.
        val scope = ownerScope(project, declaringFile)
        val helper = PsiSearchHelper.getInstance(project)
        val docManager = PsiDocumentManager.getInstance(project)

        // Collect first, edit after: mutating documents while the word-search walks them is unsafe.
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

        edits.forEach { (doc, offsets) ->
            offsets.sortedDescending().forEach { start ->
                doc.replaceString(start, start + oldKey.length, newKey)
            }
            docManager.commitDocument(doc)
        }

        if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            touchedKtFiles.forEach { fixCmpImport(project, it, newKey) }
        }
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

    private fun fixCmpImport(project: Project, ktFile: KtFile, newKey: String) {
        val vf = ktFile.virtualFile ?: return
        val resPkg = CmpModuleUtil.findResPackage(project, vf, ktFile) ?: return
        KtImportUtil.ensureImport(ktFile, "$resPkg.$newKey")
        KtImportUtil.optimizeImports(ktFile)
    }
}
