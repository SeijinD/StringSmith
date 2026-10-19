package com.seijind.stringsmith.inspections

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.seijind.stringsmith.extract.ExtractContextKind
import com.seijind.stringsmith.extract.ModuleRootUtil
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression

/** Whether a hardcoded string is text the user sees, or a key, route, tag or other technical value. */
enum class StringRole { UI, NON_UI }

/**
 * Decides which hardcoded strings the two hardcoded-string inspections report. Inside a `@Composable` a
 * string is UI text unless it goes to a known technical argument; elsewhere it is technical unless it
 * goes to a known UI sink (`Toast.makeText`, `setText`, `title = …`). Without type resolution these
 * are name-based heuristics.
 */
object HardcodedStringRules {

    /** Files never inspected: tests, generated code, Gradle scripts and build logic, benchmark modules. */
    fun isExcludedFile(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return false
        if (vf.name.endsWith(".kts")) return true
        val segments = relativeSegments(file, vf)
        if (segments.any { it == "build" || it == "buildSrc" || it == "build-logic" }) return true
        if (isTestSourceSet(segments)) return true
        if (ProjectFileIndex.getInstance(file.project).isInTestSourceContent(vf)) return true
        return isTestOnlyModule(vf)
    }

    /** The role of a Kotlin string of the given extract [kind], or null when it is never reported. */
    fun roleOf(expr: KtStringTemplateExpression, kind: ExtractContextKind): StringRole? {
        if (isIgnored(expr)) return null
        if (isTechnicalPosition(expr)) return StringRole.NON_UI
        if (isUiSink(expr)) return StringRole.UI
        return if (kind == ExtractContextKind.COMPOSABLE) StringRole.UI else StringRole.NON_UI
    }

    private fun relativeSegments(file: PsiFile, vf: VirtualFile): List<String> {
        val base = file.project.basePath?.let { vf.fileSystem.findFileByPath(it) }
        val path = base?.let { VfsUtilCore.getRelativePath(vf, it) } ?: vf.path
        return path.split('/').dropLast(1)
    }

    // `src/test`, `src/androidTest`, `src/commonTest`, `src/androidUnitTest`, `src/testDebug`, ...
    // Covers projects the IDE has not synced yet, where test roots are not marked.
    private fun isTestSourceSet(segments: List<String>): Boolean =
        segments.zipWithNext().any { (dir, sourceSet) ->
            dir == "src" && (sourceSet.startsWith("test") || sourceSet.contains("Test"))
        }

    // Benchmark and baseline-profile modules hold test code under src/main.
    private fun isTestOnlyModule(vf: VirtualFile): Boolean {
        val gradle = ModuleRootUtil.findModuleRoot(vf)?.let { ModuleRootUtil.readGradleText(it) } ?: return false
        return TEST_ONLY_PLUGIN_MARKERS.any { gradle.contains(it) }
    }

    private fun isIgnored(expr: KtStringTemplateExpression): Boolean {
        if (!hasLetters(expr)) return true
        if (PsiTreeUtil.getParentOfType(expr, KtAnnotationEntry::class.java, true) != null) return true
        if (isConstInitializer(expr)) return true
        if (PsiTreeUtil.getParentOfType(expr, KtThrowExpression::class.java, true) != null) return true
        if (isInPreviewParameterProvider(expr)) return true
        // Exception constructors only as the direct call: a composable named `ShowError { }` is still UI.
        val directCallee = directCall(expr)?.let { calleeName(it) }
        if (directCallee != null && (directCallee.endsWith("Exception") || directCallee.endsWith("Error"))) return true
        return isInsideIgnoredCall(expr)
    }

    // Also covers lazy messages such as `require(x) { "Missing" }` or `logger.debug { "Loaded" }`.
    private fun isInsideIgnoredCall(expr: KtStringTemplateExpression): Boolean {
        val ignoreLogging = StringSmithSettings.getInstance().ignoreLoggingStrings
        var call = PsiTreeUtil.getParentOfType(expr, KtCallExpression::class.java, true)
        while (call != null) {
            val callee = calleeName(call)
            if (callee in ALWAYS_IGNORED_CALLS) return true
            if (ignoreLogging && isLoggingCall(call, callee)) return true
            call = PsiTreeUtil.getParentOfType(call, KtCallExpression::class.java, true)
        }
        return false
    }

    // Only the literal text counts: `"$count"` or `"--"` has nothing to translate.
    private fun hasLetters(expr: KtStringTemplateExpression): Boolean =
        expr.entries.filterIsInstance<KtLiteralStringTemplateEntry>().any { entry -> entry.text.any { it.isLetter() } }

    private fun isConstInitializer(expr: KtStringTemplateExpression): Boolean {
        val prop = PsiTreeUtil.getParentOfType(expr, KtProperty::class.java, true) ?: return false
        return prop.hasModifier(KtTokens.CONST_KEYWORD) &&
            prop.initializer?.let { PsiTreeUtil.isAncestor(it, expr, false) } == true
    }

    private fun isInPreviewParameterProvider(expr: KtStringTemplateExpression): Boolean {
        val owner = PsiTreeUtil.getParentOfType(expr, KtClassOrObject::class.java, true) ?: return false
        return owner.superTypeListEntries.any { it.typeReference?.text?.contains("PreviewParameterProvider") == true }
    }

    private fun isLoggingCall(call: KtCallExpression, callee: String?): Boolean {
        if (callee in LOGGING_CALLS) return true
        val receiver = (call.parent as? KtQualifiedExpression)?.receiverExpression?.text?.substringAfterLast('.')?.trim()
        return receiver in LOGGING_RECEIVERS
    }

    private fun isTechnicalPosition(expr: KtStringTemplateExpression): Boolean {
        val parent = expr.parent
        if (parent is KtBinaryExpression && parent.operationToken in COMPARISON_TOKENS) return true
        if (parent is KtWhenConditionWithExpression) return true
        val argument = parent as? KtValueArgument
        if (argument?.getArgumentName()?.asName?.asString() in TECHNICAL_ARGUMENT_NAMES) return true
        val callee = directCall(expr)?.let { calleeName(it) } ?: return false
        return callee in TECHNICAL_CALLS
    }

    private fun isUiSink(expr: KtStringTemplateExpression): Boolean {
        val parent = expr.parent
        if (parent is KtBinaryExpression && parent.operationToken == KtTokens.EQ && parent.right == expr) {
            return assignedName(parent.left) in UI_PROPERTIES
        }
        val argument = parent as? KtValueArgument ?: return false
        if (argument.getArgumentName()?.asName?.asString() in UI_ARGUMENT_NAMES) return true
        val call = directCall(expr) ?: return false
        val callee = calleeName(call) ?: return false
        if (callee in UI_CALLS) return true
        val receiver = (call.parent as? KtQualifiedExpression)?.receiverExpression?.text?.substringAfterLast('.')?.trim()
        return UI_RECEIVER_CALLS[receiver]?.contains(callee) == true
    }

    private fun assignedName(left: PsiElement?): String? = when (left) {
        is KtNameReferenceExpression -> left.getReferencedName()
        is KtQualifiedExpression -> (left.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
        else -> null
    }

    /** The call [expr] is a direct value argument of, e.g. `Text("x")` but not `Column { Text("x") }`. */
    private fun directCall(expr: KtStringTemplateExpression): KtCallExpression? {
        val argument = expr.parent as? KtValueArgument ?: return null
        return PsiTreeUtil.getParentOfType(argument, KtCallExpression::class.java, true)
            ?.takeIf { call -> call.valueArguments.any { it == argument } }
    }

    private fun calleeName(call: KtCallExpression): String? =
        (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

    private val COMPARISON_TOKENS = setOf(KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ, KtTokens.IN_KEYWORD, KtTokens.NOT_IN)

    private val TEST_ONLY_PLUGIN_MARKERS = listOf("com.android.test", "plugins.android.test", "baselineprofile")

    private val ALWAYS_IGNORED_CALLS = setOf("TODO", "error", "require", "check", "requireNotNull", "checkNotNull", "assert")

    private val LOGGING_CALLS = setOf("println", "print")
    private val LOGGING_RECEIVERS = setOf("Log", "Timber", "Logger", "Napier", "log", "logger")

    // Calls whose string arguments are keys, routes, tags, paths or patterns, never UI text.
    private val TECHNICAL_CALLS = setOf(
        "testTag", "navigate", "composable", "navigation", "dialog", "key", "remember", "rememberSaveable",
        "LaunchedEffect", "DisposableEffect", "produceState", "painterResource", "imageResource",
        "vectorResource", "Font", "readBytes", "getUri", "Regex", "toRegex", "SimpleDateFormat", "ofPattern",
        "Intent", "putExtra", "getStringExtra", "getIntExtra", "getBooleanExtra", "getLongExtra",
        "getParcelableExtra", "hasExtra", "putString", "putInt", "putLong", "putBoolean", "putFloat",
        "getString", "getInt", "getLong", "getBoolean", "getFloat", "contains", "remove",
        "getSharedPreferences", "stringPreferencesKey", "intPreferencesKey", "booleanPreferencesKey",
        "longPreferencesKey", "named", "qualifier", "pushStringAnnotation", "getStringAnnotations",
        "withTag", "parse", "startsWith", "endsWith", "split", "replace", "substringBefore", "substringAfter",
        "equals", "addCategory", "getSystemService", "createNotificationChannel"
    )
    private val TECHNICAL_ARGUMENT_NAMES = setOf("route", "key", "tag", "testTag", "contentType", "id", "action")

    // Calls whose string arguments are shown to the user wherever they are made.
    private val UI_CALLS = setOf(
        "setText", "setTitle", "setSubtitle", "setMessage", "setHint", "setContentDescription",
        "setTooltipText", "setError", "setPrompt", "setSummary", "setPositiveButton", "setNegativeButton",
        "setNeutralButton", "setContentTitle", "setContentText", "setSubText", "setTicker", "setLabel",
        "showSnackbar", "setAction"
    )
    private val UI_RECEIVER_CALLS = mapOf("Toast" to setOf("makeText"), "Snackbar" to setOf("make"))
    private val UI_ARGUMENT_NAMES = setOf(
        "text", "label", "title", "subtitle", "message", "hint", "placeholder", "contentDescription",
        "description", "actionLabel", "supportingText", "errorMessage", "confirmText", "dismissText"
    )
    private val UI_PROPERTIES = setOf("text", "title", "subtitle", "hint", "contentDescription", "error", "tooltipText", "summary")
}
