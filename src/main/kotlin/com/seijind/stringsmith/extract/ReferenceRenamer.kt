package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import org.jetbrains.kotlin.psi.KtFile
import kotlin.experimental.or

/**
 * Renames every project-wide reference to a string key when its declaration is renamed: Android
 * `R.string.old`/`@string/old` and Compose Multiplatform `Res.string.old`. The `strings.xml` files
 * themselves are renamed separately by the caller, so they are skipped here.
 */
object ReferenceRenamer {

    fun rename(project: Project, oldKey: String, newKey: String, system: ResourceSystem) {
        val prefixes = when (system) {
            ResourceSystem.ANDROID -> listOf(ResourceSystem.ANDROID_REF_PREFIX, ResourceSystem.XML_REF_PREFIX)
            ResourceSystem.COMPOSE_MULTIPLATFORM -> listOf(ResourceSystem.CMP_REF_PREFIX)
        }
        val helper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val docManager = PsiDocumentManager.getInstance(project)

        // Collect first, edit after: mutating documents while the word-search is walking them is unsafe.
        // Per document, offsets are deduped (a leaf can be visited more than once) and applied descending.
        val edits = LinkedHashMap<Document, MutableSet<Int>>()
        val touchedKtFiles = LinkedHashSet<KtFile>()

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
                                val keyStart = baseOffset + idx + prefix.length
                                edits.getOrPut(doc) { sortedSetOf() }.add(keyStart)
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
            (UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS),
            true
        )

        edits.forEach { (doc, offsets) ->
            offsets.sortedDescending().forEach { start ->
                doc.replaceString(start, start + oldKey.length, newKey)
            }
            docManager.commitDocument(doc)
        }

        // CMP imports the per-key accessor (`package.old`); add the new import and let optimize drop the stale one.
        if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            touchedKtFiles.forEach { fixCmpImport(project, it, newKey) }
        }
    }

    private fun fixCmpImport(project: Project, ktFile: KtFile, newKey: String) {
        val vf = ktFile.virtualFile ?: return
        val resPkg = CmpModuleUtil.findResPackage(project, vf, ktFile) ?: return
        KtImportUtil.ensureImport(ktFile, "$resPkg.$newKey")
        KtImportUtil.optimizeImports(ktFile)
    }
}
