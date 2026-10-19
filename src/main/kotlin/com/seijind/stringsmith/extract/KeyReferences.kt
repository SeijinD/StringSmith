package com.seijind.stringsmith.extract

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope

/**
 * Finds references to a string key (`R.string.key`, `Res.string.key`, `@string/key`) through the word
 * index. Shared by the usages popup, the unused-string inspection and the key renamer.
 */
object KeyReferences {

    val ALL_PREFIXES = listOf(
        ResourceSystem.ANDROID_REF_PREFIX,
        ResourceSystem.CMP_REF_PREFIX,
        ResourceSystem.XML_REF_PREFIX
    )

    // Compose Multiplatform writes its `Res` accessors (`val Res.string.key`) under build/generated/;
    // they would make every CMP key look referenced.
    private const val GENERATED_PATH_MARKER = "/build/generated/"

    /**
     * Calls [onReference] with the file and the key's start offset for every reference to [key] in
     * [scope]. Each occurrence is reported once. Stops as soon as [onReference] returns false.
     */
    fun forEach(
        project: Project,
        scope: SearchScope,
        key: String,
        prefixes: List<String>,
        searchContext: Short,
        onReference: (file: PsiFile, keyStart: Int) -> Boolean
    ) {
        if (key.isEmpty()) return
        val seen = HashSet<Pair<PsiFile, Int>>()
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, offsetInElement ->
                ProgressManager.checkCanceled()
                val file = element.containingFile ?: return@processElementsWithWord true
                if (file.virtualFile?.path?.contains(GENERATED_PATH_MARKER) == true) return@processElementsWithWord true
                // The index reports the same occurrence once per enclosing element; dedupe by absolute offset.
                val keyStart = element.textRange.startOffset + offsetInElement
                if (!seen.add(file to keyStart)) return@processElementsWithWord true
                if (prefixAt(file.viewProvider.contents, keyStart, key, prefixes) == null) {
                    return@processElementsWithWord true
                }
                onReference(file, keyStart)
            },
            scope,
            key,
            searchContext,
            true
        )
    }

    /** The prefix in [prefixes] that makes [key] at [keyStart] in [text] a resource reference, else null. */
    fun prefixAt(text: CharSequence, keyStart: Int, key: String, prefixes: List<String>): String? {
        val keyEnd = keyStart + key.length
        if (keyStart < 0 || keyEnd > text.length || !text.subSequence(keyStart, keyEnd).contentEquals(key)) return null
        text.getOrNull(keyEnd)?.let { if (it.isIdentifierChar()) return null }
        return prefixes.firstOrNull { prefix ->
            val start = keyStart - prefix.length
            start >= 0 && text.subSequence(start, keyStart).contentEquals(prefix) && isOwnQualifier(text, start)
        }
    }

    // `R.string.x` may be fully qualified (`com.example.R.string.x`) but must not be `android.R.string.x`
    // (a platform string) or part of a longer name (`MyR.string.x`, `MyRes.string.x`).
    private fun isOwnQualifier(text: CharSequence, prefixStart: Int): Boolean {
        val before = text.getOrNull(prefixStart - 1) ?: return true
        if (before.isIdentifierChar()) return false
        if (before != '.') return true
        return !text.subSequence(0, prefixStart).endsWith("android.")
    }

    private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'
}
