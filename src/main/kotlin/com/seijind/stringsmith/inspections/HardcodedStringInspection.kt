package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlAttributeValue
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.ExtractContext
import com.seijind.stringsmith.extract.ExtractTarget
import com.seijind.stringsmith.extract.ExtractValidator
import com.seijind.stringsmith.intentions.ExtractStringResourceCoreIntention
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class HardcodedStringInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = StringSmithSettings.getInstance()
        if (!settings.inspectionEnabled) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is KtStringTemplateExpression -> visitKotlin(element)
                    is XmlAttributeValue -> visitXml(element)
                }
            }

            private fun visitKotlin(expr: KtStringTemplateExpression) {
                if (ExtractContext.isIgnorableForInspection(expr)) return
                // Cheap reject before the context-classification walks in fromKotlin: a literal with no
                // interpolation whose (trimmed) text is below the minimum can't pass validate anyway.
                if (isDefinitelyTooShort(expr)) return
                val file = expr.containingFile ?: return
                val target = ExtractContext.fromKotlin(expr, file) ?: return
                reportIfExtractable(target, expr, settings)
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
                reportIfExtractable(target, attr, settings)
            }

            private fun reportIfExtractable(target: ExtractTarget, anchor: PsiElement, settings: StringSmithSettings) {
                if (ExtractValidator.validate(target, settings) !is ExtractValidator.Result.Ok) return
                holder.registerProblem(
                    anchor,
                    StringSmithBundle.message("inspection.hardcodedString.message"),
                    com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    IntentionWrapper(ExtractStringResourceCoreIntention())
                )
            }
        }
    }
}

