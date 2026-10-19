package com.seijind.stringsmith.extract

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

object BatchScanner {

    /** [system] is the target's resource system; templates whose arguments it cannot carry are left out. */
    fun scan(
        file: PsiFile,
        settings: StringSmithSettings = StringSmithSettings.getInstance(),
        system: ResourceSystem = ResourceSystem.ANDROID
    ): List<ExtractTarget> {
        val raw = when (file) {
            is KtFile -> scanKotlin(file)
            else -> scanXml(file)
        }
        return raw.mapNotNull { target ->
            when (val r = ExtractValidator.validate(target, settings)) {
                is ExtractValidator.Result.Ok -> r.target
                is ExtractValidator.Result.Rejected -> null
            }
        }.filterNot { Replacement.dropsFormatArgs(it, system) }
            // Dropped lossy templates no longer hide the literals nested in them.
            .distinctBy { startOffsetOf(it) }.let(::dropNested)
    }

    // A literal nested in another candidate's template (`"Hi ${name ?: "Guest"}"`) would be replaced
    // first and leave the outer candidate's range stale, so only the outer one is kept.
    private fun dropNested(targets: List<ExtractTarget>): List<ExtractTarget> {
        val candidates = targets.mapNotNull { it.kotlin }.toSet()
        return targets.filter { target ->
            val expr = target.kotlin ?: return@filter true
            generateSequence(PsiTreeUtil.getParentOfType(expr, KtStringTemplateExpression::class.java, true)) {
                PsiTreeUtil.getParentOfType(it, KtStringTemplateExpression::class.java, true)
            }.none { it in candidates }
        }
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
