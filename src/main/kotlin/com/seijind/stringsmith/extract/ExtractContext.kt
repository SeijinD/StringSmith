package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtValueArgument

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
    val containingFile: PsiFile,
    val formatArgs: List<String> = emptyList()
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
        if (kt != null) return fromKotlin(kt, file)
        val xml = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
        if (xml != null) return fromXml(xml, file)
        return null
    }

    fun fromXml(attr: XmlAttributeValue, file: PsiFile): ExtractTarget? {
        if (!isAndroidResourceXml(file)) return null
        val attribute = attr.parent as? XmlAttribute ?: return null
        if (!isExtractableTextAttribute(attribute)) return null
        val value = attr.value
        if (value.startsWith("@") || value.startsWith("?")) return null
        // attr.value keeps entities encoded (`A &amp; B`); the written entry re-encodes them.
        val decoded = attribute.displayValue ?: value
        return ExtractTarget(xml = attr, rawValue = decoded, kind = ExtractContextKind.XML_LAYOUT, containingFile = file)
    }

    // Only user-facing text attributes are extractable. Enum/dimension/reference attributes
    // (layout_width="match_parent", textSize="16sp", orientation="vertical", …) must never be
    // offered an @string/ replacement: it would break the layout. tools: is design-time only.
    private fun isExtractableTextAttribute(attribute: XmlAttribute): Boolean {
        val name = attribute.name
        if (name.startsWith("tools:")) return false
        return name.substringAfterLast(':') in TEXT_ATTRIBUTES
    }

    private val TEXT_ATTRIBUTES = setOf(
        "text", "hint", "contentDescription", "label", "title", "subtitle",
        "summary", "description", "message", "tooltipText", "prompt",
        "dialogTitle", "dialogMessage", "text1", "text2",
        "queryHint", "helperText", "placeholderText", "error",
        "prefixText", "suffixText", "titleText", "subtitleText"
    )

    /**
     * Files where extraction can apply at all: Kotlin sources and Android resource layouts.
     * Never res/values* (a strings.xml is the extract *target*, not a source) or unrelated files.
     * Used to gate the extract editor actions so they stay hidden where extraction is meaningless.
     */
    fun isExtractEligibleFile(file: PsiFile): Boolean = file is KtFile || isAndroidResourceXml(file)

    // Restrict to res/<type>/ (layout, menu, …) where @string/ is valid; excludes res/values*, manifest, unrelated XML.
    private fun isAndroidResourceXml(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return false
        val parentName = vf.parent?.name ?: return false
        if (parentName == "values" || parentName.startsWith("values-")) return false
        return vf.parent?.parent?.name == "res"
    }

    fun fromKotlin(expr: KtStringTemplateExpression, file: PsiFile): ExtractTarget? {
        val kind = classifyKotlin(expr)
        val hasExpressions = expr.entries.any { it is KtStringTemplateEntryWithExpression }
        if (!hasExpressions) {
            val value = expr.entries.joinToString("") { if (it is KtEscapeStringTemplateEntry) androidText(it) else it.text }
            return ExtractTarget(kotlin = expr, rawValue = value, kind = kind, containingFile = file)
        }
        if (!StringSmithSettings.getInstance().detectFormatArgs) return null
        if (kind == ExtractContextKind.KOTLIN_GENERIC) return null
        val sb = StringBuilder()
        val args = mutableListOf<String>()
        for (entry in expr.entries) {
            when (entry) {
                is KtLiteralStringTemplateEntry -> sb.append(entry.text.replace("%", "%%"))
                is KtEscapeStringTemplateEntry -> sb.append(androidText(entry).replace("%", "%%"))
                is KtSimpleNameStringTemplateEntry -> {
                    args.add(entry.expression?.text ?: entry.text.removePrefix("$"))
                    sb.append("%${args.size}\$s")
                }
                is KtStringTemplateEntryWithExpression -> {
                    args.add(entry.expression?.text ?: "")
                    sb.append("%${args.size}\$s")
                }
            }
        }
        return ExtractTarget(
            kotlin = expr,
            rawValue = sb.toString(),
            kind = kind,
            containingFile = file,
            formatArgs = args
        )
    }

    // rawValue holds Android string-resource text. Kotlin's `\n`, `\t`, `\\` mean the same there and are
    // kept; quote and `\$` escapes are unescaped because encodeXml escapes quotes itself.
    private fun androidText(entry: KtEscapeStringTemplateEntry): String =
        if (entry.text in KOTLIN_ONLY_ESCAPES || entry.text.startsWith("\\u")) entry.unescapedValue else entry.text

    private val KOTLIN_ONLY_ESCAPES = setOf("\\\"", "\\'", "\\$")

    private fun classifyKotlin(expr: KtStringTemplateExpression): ExtractContextKind {
        val lambdaScope = lambdaScopeOf(expr)
        if (lambdaScope == LambdaScope.COMPOSABLE) return ExtractContextKind.COMPOSABLE

        // Inside an event or effect lambda the enclosing @Composable doesn't make the string composable.
        if (lambdaScope == LambdaScope.NONE) {
            val fn = PsiTreeUtil.getParentOfType(expr, KtNamedFunction::class.java, true)
            if (fn != null && hasComposable(fn)) return ExtractContextKind.COMPOSABLE
        }

        val cls = PsiTreeUtil.getParentOfType(expr, KtClass::class.java, true)
        if (cls != null) {
            if (extendsAndroidUiClass(cls)) return ExtractContextKind.ANDROID_CLASS
            if (cls.name?.let { matchesAndroidNameSuffix(it) } == true) return ExtractContextKind.ANDROID_CLASS
        }

        return ExtractContextKind.KOTLIN_GENERIC
    }

    private enum class LambdaScope { COMPOSABLE, NON_COMPOSABLE, NONE }

    private fun lambdaScopeOf(expr: KtStringTemplateExpression): LambdaScope {
        val customNames = StringSmithSettings.getInstance().customComposableLambdaFunctionSet()
        var lambda = PsiTreeUtil.getParentOfType(expr, KtLambdaExpression::class.java, true)
        while (lambda != null) {
            // Only a trailing-lambda argument can carry a composable scope (Column { }, run { }, ...). A
            // value-argument lambda such as onClick = { } is an event handler that runs outside composition.
            val lambdaArg = lambda.parent as? KtLambdaArgument ?: return LambdaScope.NON_COMPOSABLE
            val call = lambdaArg.parent as? KtCallExpression ?: return LambdaScope.NON_COMPOSABLE
            val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            if (callee in COMPOSABLE_ENTRY_CALLS || callee in customNames) return LambdaScope.COMPOSABLE
            if (callee in NON_COMPOSABLE_LAMBDA_CALLS) return LambdaScope.NON_COMPOSABLE
            lambda = PsiTreeUtil.getParentOfType(call, KtLambdaExpression::class.java, true)
        }
        return LambdaScope.NONE
    }

    private val NON_COMPOSABLE_LAMBDA_CALLS = setOf(
        "LaunchedEffect",
        "DisposableEffect",
        "SideEffect",
        "launch",
        "async",
        "remember",
        "rememberSaveable",
        "derivedStateOf"
    )

    private val COMPOSABLE_ENTRY_CALLS = setOf(
        "setContent",
        "composable",
        "composed",
        "bottomSheet",
        "dialog",
        "navigation"
    )

    private fun matchesAndroidNameSuffix(name: String): Boolean =
        ANDROID_NAME_SUFFIXES.any { name.endsWith(it) }

    private val ANDROID_NAME_SUFFIXES = listOf(
        "Activity",
        "Fragment",
        "Service"
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
        "Service"
    )

    /**
     * Contexts where a Kotlin string literal is technical rather than user-facing UI text, so the
     * hardcoded-string inspection should stay quiet. Consulted ONLY by the inspection; manual extract
     * (the action / Alt+Enter intention) still works on these, so nothing is blocked, just un-flagged.
     *
     * Annotation arguments (e.g. `@SerialName("user")`, `@Query("SELECT …")`) and `const` initializers
     * are always ignored; logging-call arguments (`Log.d`, `Timber.*`, `println`, `require`/`check`)
     * are ignored when [StringSmithSettings.ignoreLoggingStrings] is on.
     */
    fun isIgnorableForInspection(expr: KtStringTemplateExpression): Boolean {
        if (PsiTreeUtil.getParentOfType(expr, KtAnnotationEntry::class.java, true) != null) return true

        val prop = PsiTreeUtil.getParentOfType(expr, KtProperty::class.java, true)
        if (prop != null &&
            prop.hasModifier(KtTokens.CONST_KEYWORD) &&
            prop.initializer?.let { PsiTreeUtil.isAncestor(it, expr, false) } == true
        ) return true

        return StringSmithSettings.getInstance().ignoreLoggingStrings && isLoggingArgument(expr)
    }

    private fun isLoggingArgument(expr: KtStringTemplateExpression): Boolean {
        val call = PsiTreeUtil.getParentOfType(expr, KtCallExpression::class.java, true) ?: return false
        val argList = call.valueArgumentList ?: return false
        val arg = PsiTreeUtil.getParentOfType(expr, KtValueArgument::class.java, true) ?: return false
        // Must be a plain value argument of this call, not e.g. a string inside a trailing lambda.
        if (!PsiTreeUtil.isAncestor(argList, arg, false)) return false
        val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (callee in LOGGING_CALLS) return true
        val receiver = (call.parent as? KtDotQualifiedExpression)
            ?.receiverExpression?.text?.substringAfterLast('.')?.trim()
        return receiver in LOGGING_RECEIVERS
    }

    private val LOGGING_CALLS = setOf("println", "print", "error", "require", "check", "checkNotNull", "requireNotNull")
    private val LOGGING_RECEIVERS = setOf("Log", "Timber", "Logger")

    fun isInsidePreviewComposable(target: ExtractTarget): Boolean {
        val expr = target.kotlin ?: return false
        val fn = PsiTreeUtil.getParentOfType(expr, KtNamedFunction::class.java, true) ?: return false
        return fn.annotationEntries.any { it.shortName?.asString() == "Preview" }
    }

}
