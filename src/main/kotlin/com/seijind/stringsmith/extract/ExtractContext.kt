package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

enum class ExtractContextKind {
    COMPOSABLE,
    ANDROID_CLASS,
    KOTLIN_GENERIC,
    XML_LAYOUT
}

data class ExtractTarget(
    val kotlin: KtStringTemplateExpression? = null,
    val xml: XmlAttributeValue? = null,
    val rawValue: String,
    val kind: ExtractContextKind,
    val containingFile: PsiFile
)

object ExtractContext {

    fun detect(file: PsiFile, editor: Editor): ExtractTarget? {
        val offset = editor.caretModel.offset
        val forward = file.findElementAt(offset)?.let { detectFrom(it, file) }
        if (forward != null) return forward
        if (offset > 0) {
            val backward = file.findElementAt(offset - 1)?.let { detectFrom(it, file) }
            if (backward != null) return backward
        }
        return null
    }

    private fun detectFrom(element: PsiElement, file: PsiFile): ExtractTarget? {
        val kt = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
        if (kt != null) {
            if (kt.entries.any { it !is KtLiteralStringTemplateEntry }) return null
            val value = kt.entries.joinToString("") { it.text }
            val kind = classifyKotlin(kt)
            return ExtractTarget(kotlin = kt, rawValue = value, kind = kind, containingFile = file)
        }
        val xml = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
        if (xml != null) {
            val value = xml.value
            if (value.startsWith("@") || value.startsWith("?")) return null
            return ExtractTarget(xml = xml, rawValue = value, kind = ExtractContextKind.XML_LAYOUT, containingFile = file)
        }
        return null
    }

    private fun classifyKotlin(expr: KtStringTemplateExpression): ExtractContextKind {
        val fn = PsiTreeUtil.getParentOfType(expr, KtNamedFunction::class.java, true)
        if (fn != null && hasComposable(fn)) return ExtractContextKind.COMPOSABLE

        val cls = PsiTreeUtil.getParentOfType(expr, KtClass::class.java, true)
        if (cls != null) {
            if (extendsAndroidUiClass(cls)) return ExtractContextKind.ANDROID_CLASS
            if (cls.name?.let { matchesAndroidNameSuffix(it) } == true) return ExtractContextKind.ANDROID_CLASS
        }

        return ExtractContextKind.KOTLIN_GENERIC
    }

    private fun matchesAndroidNameSuffix(name: String): Boolean =
        ANDROID_NAME_SUFFIXES.any { name.endsWith(it) }

    private val ANDROID_NAME_SUFFIXES = listOf(
        "Activity",
        "Fragment",
        "Service",
        "Receiver",
        "Provider",
        "Worker"
    )

    private fun hasComposable(fn: KtNamedFunction): Boolean =
        fn.annotationEntries.any { it.isComposable() }

    private fun KtAnnotationEntry.isComposable(): Boolean {
        val name = shortName?.asString() ?: return false
        return name == "Composable"
    }

    private fun extendsAndroidUiClass(cls: KtClass): Boolean =
        cls.superTypeListEntries.any { matchesAndroidSuper(it) }

    private fun matchesAndroidSuper(entry: KtSuperTypeListEntry): Boolean {
        val text = entry.typeReference?.text ?: return false
        val short = text.substringAfterLast('.').substringBefore('<').trim()
        return short in ANDROID_BASE_CLASSES
    }

    private val ANDROID_BASE_CLASSES = setOf(
        "Activity",
        "AppCompatActivity",
        "ComponentActivity",
        "FragmentActivity",
        "Fragment",
        "DialogFragment",
        "BottomSheetDialogFragment",
        "PreferenceFragmentCompat",
        "Service",
        "BroadcastReceiver",
        "View",
        "ViewGroup",
        "FrameLayout",
        "LinearLayout",
        "ConstraintLayout"
    )

    fun isKotlinFile(file: PsiFile): Boolean = file is KtFile

    fun fromKotlin(expr: KtStringTemplateExpression, file: PsiFile): ExtractTarget? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        val value = expr.entries.joinToString("") { it.text }
        val kind = classifyKotlin(expr)
        return ExtractTarget(kotlin = expr, rawValue = value, kind = kind, containingFile = file)
    }

    fun isInsidePreviewComposable(target: ExtractTarget): Boolean {
        val expr = target.kotlin ?: return false
        val fn = PsiTreeUtil.getParentOfType(expr, KtNamedFunction::class.java, true) ?: return false
        return fn.annotationEntries.any { it.shortName?.asString() == "Preview" }
    }

    fun fromXml(attr: XmlAttributeValue, file: PsiFile): ExtractTarget? {
        val value = attr.value
        if (value.startsWith("@") || value.startsWith("?")) return null
        return ExtractTarget(xml = attr, rawValue = value, kind = ExtractContextKind.XML_LAYOUT, containingFile = file)
    }
}
