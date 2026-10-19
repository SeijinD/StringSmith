package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlAttributeValue
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.ExtractContext
import com.seijind.stringsmith.extract.ExtractTarget
import com.seijind.stringsmith.extract.ExtractValidator
import com.seijind.stringsmith.extract.Replacement
import com.seijind.stringsmith.extract.ResourceSystem
import com.seijind.stringsmith.extract.StringsXmlUtil
import com.seijind.stringsmith.intentions.ExtractStringResourceIntention
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** Hardcoded text the user sees: Compose UI, UI calls such as `Toast.makeText`, XML layouts and the manifest. */
class HardcodedUiStringInspection : HardcodedStringInspection(StringRole.UI, "inspection.hardcodedString.message")

/** Every other hardcoded Kotlin string: keys, routes, tags and text in non-UI code. Off by default. */
class HardcodedNonUiStringInspection : HardcodedStringInspection(StringRole.NON_UI, "inspection.hardcodedNonUiString.message")

/** Reports hardcoded strings of one [role] (see [HardcodedStringRules]) with the Extract quick-fix. */
abstract class HardcodedStringInspection(
    private val role: StringRole,
    private val messageKey: String
) : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (HardcodedStringRules.isExcludedFile(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        val settings = StringSmithSettings.getInstance()
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is KtStringTemplateExpression -> visitKotlin(element)
                    is XmlAttributeValue -> if (role == StringRole.UI) visitXml(element)
                }
            }

            private fun visitKotlin(expr: KtStringTemplateExpression) {
                // Cheap reject before the context-classification walks in fromKotlin: a literal with no
                // interpolation whose (trimmed) text is below the minimum can't pass validate anyway.
                if (isDefinitelyTooShort(expr)) return
                val target = ExtractContext.fromKotlin(expr, expr.containingFile) ?: return
                if (HardcodedStringRules.roleOf(expr, target.kind) != role) return
                reportIfExtractable(target, expr)
            }

            private fun isDefinitelyTooShort(expr: KtStringTemplateExpression): Boolean {
                if (expr.entries.any { it is KtStringTemplateEntryWithExpression }) return false
                val content = expr.entries.joinToString("") { it.text }
                val effective = if (settings.trimWhitespace) content.trim() else content
                return effective.length < settings.minStringLength
            }

            private fun visitXml(attr: XmlAttributeValue) {
                val file = attr.containingFile ?: return
                val target = ExtractContext.fromXml(attr, file) ?: return
                reportIfExtractable(target, attr)
            }

            private fun reportIfExtractable(target: ExtractTarget, anchor: PsiElement) {
                if (ExtractValidator.validate(target, settings) !is ExtractValidator.Result.Ok) return
                if (losesFormatArgs(target)) return
                holder.registerProblem(
                    anchor,
                    StringSmithBundle.message(messageKey),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    IntentionWrapper(ExtractStringResourceIntention())
                )
            }
        }
    }

    // Only templates with arguments can lose them; resolving the target strings.xml is skipped otherwise.
    private fun losesFormatArgs(target: ExtractTarget): Boolean {
        if (target.formatArgs.isEmpty()) return false
        val file = target.containingFile
        val stringsXml = StringsXmlUtil.findDefaultStringsXml(file.project, file.virtualFile) ?: return false
        return Replacement.dropsFormatArgs(target, ResourceSystem.of(stringsXml))
    }
}
