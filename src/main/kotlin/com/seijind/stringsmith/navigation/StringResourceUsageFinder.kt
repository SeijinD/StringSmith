package com.seijind.stringsmith.navigation

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.seijind.stringsmith.extract.ResourceSystem
import kotlin.experimental.or

/**
 * Locates code/resource elements that reference a string resource [key]:
 * `R.string.key`, `Res.string.key` (CMP) or `@string/key`.
 *
 * Shares the project-wide word-index strategy used by the unused-string inspection, but collects the
 * matching PSI elements instead of a boolean — so Alt+Click can navigate straight to them. Results are
 * wrapped as [StringResourceUsageItem]s so the navigation popup reads "<snippet>  (File.kt:line)".
 */
object StringResourceUsageFinder {

    fun findUsages(project: Project, key: String): List<PsiElement> {
        if (key.isEmpty()) return emptyList()
        val helper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val docManager = PsiDocumentManager.getInstance(project)

        // The word index can hand back several nested elements for the same occurrence (leaf + enclosing
        // expressions); collapse them per source line, keeping the narrowest element so navigation lands
        // on the reference itself rather than its surrounding statement.
        val narrowestPerLine = HashMap<LineKey, Match>()

        helper.processElementsWithWord(
            { element, offsetInElement ->
                ProgressManager.checkCanceled()
                val owner = element.containingFile ?: return@processElementsWithWord true
                if (owner.name == "strings.xml") return@processElementsWithWord true
                val vFile = owner.virtualFile
                if (vFile != null && vFile.path.contains(GENERATED_PATH_MARKER)) {
                    return@processElementsWithWord true
                }
                val text = element.text ?: return@processElementsWithWord true
                // The word index anchors `key` at offsetInElement; widen the window to fit the longest
                // prefix ("Res.string.", 11 chars) in front of it plus a little slack each side.
                val start = maxOf(0, offsetInElement - LOOK_BEHIND)
                val end = minOf(text.length, offsetInElement + key.length + LOOK_AHEAD)
                if (REFERENCE_PREFIXES.none { text.substring(start, end).contains("$it$key") }) {
                    return@processElementsWithWord true
                }
                val doc = docManager.getDocument(owner) ?: return@processElementsWithWord true
                // `element` may be a large enclosing node; the real occurrence sits at its start +
                // offsetInElement. Resolve the precise leaf there so the line, snippet and caret all
                // point at the reference, not at the start of some block that merely contains it.
                val absolute = (element.textRange.startOffset + offsetInElement).coerceIn(0, doc.textLength)
                val leaf = owner.findElementAt(absolute) ?: element
                val line = doc.getLineNumber(absolute)
                val lineKey = LineKey(vFile?.path ?: owner.name, line)
                val current = narrowestPerLine[lineKey]
                if (current == null || leaf.textRange.length < current.element.textRange.length) {
                    narrowestPerLine[lineKey] = Match(leaf, doc, line)
                }
                true
            },
            scope,
            key,
            (UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS),
            true
        )

        return narrowestPerLine.values
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

    // Reference forms a key can appear in: Android `R.string.`/`@string/` and CMP `Res.string.`.
    private val REFERENCE_PREFIXES = listOf(
        ResourceSystem.ANDROID_REF_PREFIX,
        ResourceSystem.CMP_REF_PREFIX,
        ResourceSystem.XML_REF_PREFIX,
    )
    private val WHITESPACE = Regex("\\s+")
    private const val LOOK_BEHIND = 12
    private const val LOOK_AHEAD = 2
    private const val MAX_SNIPPET = 100

    // Compose Multiplatform writes its `Res`/`StringResource` accessors under build/generated/... ;
    // exclude that path so usages list real call sites, not the generated accessor declarations.
    private const val GENERATED_PATH_MARKER = "/build/generated/"
}
