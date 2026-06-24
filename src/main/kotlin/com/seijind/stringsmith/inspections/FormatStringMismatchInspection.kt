package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.StringsXmlUtil
import com.seijind.stringsmith.settings.StringSmithSettings

/**
 * Flags a `<string>` in a locale `strings.xml` whose format specifiers (`%s`, `%d`, `%1$s`, …) don't
 * match the default (`values/strings.xml`) entry for the same key — a dropped or retyped argument that
 * crashes at runtime with `IllegalFormatException`. Works for both Android `res/values*` and Compose
 * Multiplatform `composeResources/values*` layouts; for CMP, Android Lint provides no equivalent check.
 */
class FormatStringMismatchInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!StringSmithSettings.getInstance().formatMismatchInspectionEnabled) return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null
        val localeFile = file.virtualFile ?: return null
        val defaultFile = defaultSibling(localeFile) ?: return null
        // The default file is the baseline; it has nothing to be compared against.
        if (defaultFile == localeFile) return null

        val defaults = StringsXmlUtil.readEntries(defaultFile).associate { it.key to it.value }
        if (defaults.isEmpty()) return null

        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
            .filter { it.name == "string" && it.getAttributeValue("name") != null }

        val problems = mutableListOf<ProblemDescriptor>()
        for (tag in tags) {
            // Bail out promptly when the daemon cancels (e.g. the user keeps typing in a large file).
            ProgressManager.checkCanceled()
            val key = tag.getAttributeValue("name") ?: continue
            val defaultValue = defaults[key] ?: continue
            val mismatch = FormatSpec.analyze(defaultValue, tag.value.text) ?: continue
            val anchor = tag.getAttribute("name")?.valueElement ?: tag
            problems += manager.createProblemDescriptor(
                anchor,
                messageFor(key, mismatch),
                true,
                emptyArray(),
                ProblemHighlightType.WARNING
            )
        }
        return problems.toTypedArray()
    }

    private fun messageFor(key: String, mismatch: FormatMismatch): String = when (mismatch) {
        is FormatMismatch.Count ->
            StringSmithBundle.message("inspection.formatMismatch.count", key, mismatch.defaultCount, mismatch.actualCount)
        is FormatMismatch.Type ->
            StringSmithBundle.message("inspection.formatMismatch.type", key, mismatch.position, mismatch.default, mismatch.actual)
    }

    /** The default `values/strings.xml` sitting beside a `values-*` locale file (same `res`/`composeResources` dir). */
    private fun defaultSibling(localeFile: VirtualFile): VirtualFile? =
        localeFile.parent?.parent?.findChild("values")?.findChild("strings.xml")
}
