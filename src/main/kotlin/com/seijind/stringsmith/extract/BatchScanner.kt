package com.seijind.stringsmith.extract

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

object BatchScanner {

    fun scan(file: PsiFile, settings: StringSmithSettings = StringSmithSettings.getInstance()): List<ExtractTarget> {
        val raw = when (file) {
            is KtFile -> scanKotlin(file)
            else -> scanXml(file)
        }
        return raw.mapNotNull { target ->
            when (val r = ExtractValidator.validate(target, settings)) {
                is ExtractValidator.Result.Ok -> r.target
                is ExtractValidator.Result.Rejected -> null
            }
        }.distinctBy { startOffsetOf(it) }
    }

    private fun scanKotlin(file: KtFile): List<ExtractTarget> =
        PsiTreeUtil.findChildrenOfType(file, KtStringTemplateExpression::class.java)
            .mapNotNull { ExtractContext.fromKotlin(it, file) }

    private fun scanXml(file: PsiFile): List<ExtractTarget> =
        PsiTreeUtil.findChildrenOfType(file, XmlAttributeValue::class.java)
            .mapNotNull { ExtractContext.fromXml(it, file) }

    fun startOffsetOf(target: ExtractTarget): Int =
        target.kotlin?.textRange?.startOffset ?: target.xml?.textRange?.startOffset ?: -1
}
