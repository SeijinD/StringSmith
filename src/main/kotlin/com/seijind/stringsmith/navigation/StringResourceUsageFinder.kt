package com.seijind.stringsmith.navigation

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.seijind.stringsmith.extract.KeyReferences
import kotlin.experimental.or

/**
 * Locates code/resource elements that reference a string resource [key]:
 * `R.string.key`, `Res.string.key` (CMP) or `@string/key`.
 *
 * Results are wrapped as [StringResourceUsageItem]s so the navigation popup reads "<snippet>  (File.kt:line)".
 */
object StringResourceUsageFinder {

    fun findUsages(project: Project, key: String): List<PsiElement> {
        val docManager = PsiDocumentManager.getInstance(project)
        // Several references on one line collapse into a single popup entry.
        val perLine = LinkedHashMap<LineKey, Match>()

        KeyReferences.forEach(
            project, GlobalSearchScope.projectScope(project), key, KeyReferences.ALL_PREFIXES,
            UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS
        ) { file, keyStart ->
            val doc = docManager.getDocument(file) ?: return@forEach true
            val leaf = file.findElementAt(keyStart) ?: return@forEach true
            val line = doc.getLineNumber(keyStart)
            perLine.putIfAbsent(LineKey(file.virtualFile?.path ?: file.name, line), Match(leaf, doc, line))
            true
        }

        return perLine.values
            .sortedWith(compareBy({ it.element.containingFile?.name ?: "" }, { it.element.textRange.startOffset }))
            .map { it.toItem() }
    }

    private fun Match.toItem(): StringResourceUsageItem {
        val file = element.containingFile
        return StringResourceUsageItem(
            origin = element,
            snippet = snippetOf(doc, line),
            location = "${file?.name ?: "?"}:${line + 1}",
            fileIcon = file?.getIcon(0)
        )
    }

    private fun snippetOf(doc: Document, line: Int): String {
        val raw = doc.getText(TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line)))
        val collapsed = raw.replace(WHITESPACE, " ").trim()
        return if (collapsed.length > MAX_SNIPPET) collapsed.take(MAX_SNIPPET - 1) + "…" else collapsed
    }

    private data class LineKey(val path: String, val line: Int)
    private data class Match(val element: PsiElement, val doc: Document, val line: Int)

    private val WHITESPACE = Regex("\\s+")
    private const val MAX_SNIPPET = 100
}
