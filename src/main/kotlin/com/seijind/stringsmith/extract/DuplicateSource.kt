package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

/** Where a duplicate was triggered from a Kotlin reference, so the reference can be switched in place. */
data class DuplicateCodeRef(val ktFile: KtFile, val keyRangeStart: Int, val keyRangeEnd: Int)

/** Resolved source of a duplicate: an existing key, its owning default `strings.xml`, and per-locale values. */
data class DuplicateSource(
    val key: String,
    val system: ResourceSystem,
    val defaultFile: VirtualFile,
    val defaultValue: String,
    /**
     * Locale variant file -> that locale's existing translation of [key]. Only locales that actually
     * translate [key] are included, so the duplicate mirrors the source key's locale coverage instead
     * of copying the default value in as a fake translation.
     */
    val localeValues: Map<VirtualFile, String>,
    /**
     * Locale variants that exist but do not translate [key]; the new key is intentionally NOT written
     * to them (surfaced in the dialog so the user knows which locales are skipped).
     */
    val untranslatedLocales: List<VirtualFile>,
    /** Non-null when triggered from a `R.string`/`Res.string` reference in Kotlin. */
    val codeRef: DuplicateCodeRef?
)

object DuplicateRefParser {

    // Matches a trailing R.string.key / Res.string.key, optionally with a package qualifier (com.app.R.string.key).
    private val RE = Regex("""(?:^|\.)(R|Res)\.string\.([A-Za-z_][A-Za-z0-9_]*)$""")

    fun parse(refText: String): Pair<ResourceSystem, String>? {
        val m = RE.find(refText.trim()) ?: return null
        val system = if (m.groupValues[1] == "Res") ResourceSystem.COMPOSE_MULTIPLATFORM else ResourceSystem.ANDROID
        return system to m.groupValues[2]
    }
}

object DuplicateContext {

    /**
     * Cheap syntactic test for whether the caret sits on a `R.string.key` / `Res.string.key` reference
     * or a `<string name=…>` entry. Used by intention `isAvailable`, which fires on every Alt+Enter — so
     * unlike [detect] it must NOT scan the project or parse files. The owning default file and locale
     * values are resolved later, in [detect], from the action's `invoke`.
     */
    fun isOnResourceRef(file: PsiFile, editor: Editor): Boolean {
        val offset = editor.caretModel.offset
        return refAt(file, offset) || (offset > 0 && refAt(file, offset - 1))
    }

    private fun refAt(file: PsiFile, offset: Int): Boolean {
        val element = file.findElementAt(offset) ?: return false
        if (file is KtFile) return isCodeRef(element)
        val vf = file.virtualFile ?: return false
        if (vf.name != "strings.xml") return false
        val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return false
        return tag.name == "string" && tag.getAttributeValue("name") != null
    }

    private fun isCodeRef(element: PsiElement): Boolean {
        var qualified = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java, false)
        while (qualified != null) {
            if (DuplicateRefParser.parse(qualified.text) != null) return true
            qualified = PsiTreeUtil.getParentOfType(qualified, KtDotQualifiedExpression::class.java, true)
        }
        return false
    }

    fun detect(project: Project, file: PsiFile, editor: Editor): DuplicateSource? {
        val offset = editor.caretModel.offset
        // Try the element at the caret, then the one just before it: a caret sitting at the *end* of a
        // reference (right after `</string>` or the closing `)`) lands on the trailing token, not the key.
        detectAt(project, file, offset)?.let { return it }
        return if (offset > 0) detectAt(project, file, offset - 1) else null
    }

    private fun detectAt(project: Project, file: PsiFile, offset: Int): DuplicateSource? {
        val element = file.findElementAt(offset) ?: return null
        detectFromCode(project, file, element)?.let { return it }
        return detectFromXml(project, file, element)
    }

    private fun detectFromCode(project: Project, file: PsiFile, element: PsiElement): DuplicateSource? {
        val ktFile = file as? KtFile ?: return null
        // Walk up to the outermost dot-qualified expression that is a R.string / Res.string reference.
        var qualified = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java, false) ?: return null
        var match = DuplicateRefParser.parse(qualified.text)
        while (match == null) {
            qualified = PsiTreeUtil.getParentOfType(qualified, KtDotQualifiedExpression::class.java, true) ?: return null
            match = DuplicateRefParser.parse(qualified.text)
        }
        val (system, key) = match
        val keySelector = qualified.selectorExpression ?: return null
        val nearTo = file.virtualFile

        val defaultFile = resolveOwningDefaultFile(project, key, system, nearTo) ?: return null
        return buildSource(
            key = key,
            system = system,
            defaultFile = defaultFile,
            codeRef = DuplicateCodeRef(ktFile, keySelector.textRange.startOffset, keySelector.textRange.endOffset)
        )
    }

    private fun detectFromXml(project: Project, file: PsiFile, element: PsiElement): DuplicateSource? {
        val vf = file.virtualFile ?: return null
        if (vf.name != "strings.xml") return null
        val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return null
        if (tag.name != "string") return null
        val key = tag.getAttributeValue("name") ?: return null

        val system = ResourceSystem.of(vf)
        val defaultFile = StringsXmlUtil.findDefaultStringsXml(project, vf) ?: return null
        if (StringsXmlUtil.findValueOfKey(defaultFile, key) == null) return null
        return buildSource(key = key, system = system, defaultFile = defaultFile, codeRef = null)
    }

    /** Among default strings.xml files of the matching system, the one containing [key], nearest to [nearTo]. */
    private fun resolveOwningDefaultFile(
        project: Project,
        key: String,
        system: ResourceSystem,
        nearTo: VirtualFile?
    ): VirtualFile? {
        val candidates = StringsXmlUtil.findAllDefaultStringsXml(project)
            .filter { ResourceSystem.of(it) == system }
            .filter { StringsXmlUtil.findValueOfKey(it, key) != null }
        if (candidates.isEmpty()) return null
        if (nearTo == null) return candidates.first()
        return candidates.maxByOrNull { it.path.commonPrefixWith(nearTo.path).length }
    }

    private fun buildSource(
        key: String,
        system: ResourceSystem,
        defaultFile: VirtualFile,
        codeRef: DuplicateCodeRef?
    ): DuplicateSource? {
        val defaultValue = StringsXmlUtil.findValueOfKey(defaultFile, key) ?: return null
        val localeValues = LinkedHashMap<VirtualFile, String>()
        val untranslated = mutableListOf<VirtualFile>()
        for (variant in StringsXmlUtil.findLocaleVariants(defaultFile)) {
            val value = StringsXmlUtil.findValueOfKey(variant, key)
            if (value != null) localeValues[variant] = value else untranslated += variant
        }
        return DuplicateSource(key, system, defaultFile, defaultValue, localeValues, untranslated, codeRef)
    }
}
