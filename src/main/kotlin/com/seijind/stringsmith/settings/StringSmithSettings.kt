package com.seijind.stringsmith.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

enum class NamingConvention(val display: String) {
    SNAKE_CASE("snake_case"),
    CAMEL_CASE("camelCase");
    override fun toString(): String = display
}

enum class ActivityReplacementStyle(val display: String, val template: String) {
    GET_STRING("getString(R.string.x)", "getString(R.string.%s)"),
    REQUIRE_CONTEXT("requireContext().getString(R.string.x)", "requireContext().getString(R.string.%s)"),
    CONTEXT("context?.getString(R.string.x)", "context?.getString(R.string.%s)");
    override fun toString(): String = display
}

enum class ComposeArgStyle(val display: String, val template: String) {
    POSITIONAL("stringResource(R.string.x)", "stringResource(R.string.%s)"),
    NAMED("stringResource(id = R.string.x)", "stringResource(id = R.string.%s)");
    override fun toString(): String = display
}

@State(
    name = "StringSmithSettings",
    storages = [Storage("stringsmith.xml")]
)
@Service(Service.Level.APP)
class StringSmithSettings : PersistentStateComponent<StringSmithSettings.State> {

    data class State(
        var keyPrefix: String = "",
        var autoIncludeLocales: Boolean = true,
        var excludePreviewComposables: Boolean = true,
        var inspectionEnabled: Boolean = false,
        var ignoreLoggingStrings: Boolean = true,
        var detectFormatArgs: Boolean = true,
        var duplicateValueInspectionEnabled: Boolean = true,
        var unusedStringInspectionEnabled: Boolean = true,
        var namingConvention: NamingConvention = NamingConvention.SNAKE_CASE,
        var maxKeyLength: Int = 40,
        var minStringLength: Int = 2,
        var sortAfterExtract: Boolean = false,
        var openStringsXmlAfterExtract: Boolean = false,
        var addSourceComment: Boolean = false,
        var trimWhitespace: Boolean = true,
        var excludePatterns: String = "^[A-Z_]{2,}$\nhttps?://.*",
        var activityStyle: ActivityReplacementStyle = ActivityReplacementStyle.GET_STRING,
        var composeStyle: ComposeArgStyle = ComposeArgStyle.POSITIONAL,
        var lastTargetModulePath: String = "",
        var rememberLastModule: Boolean = true,
        var customComposableLambdaFunctions: String = "",
        var cmpResPackageOverride: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    fun resetToDefaults() {
        state = State()
    }

    var keyPrefix: String
        get() = state.keyPrefix
        set(value) { state.keyPrefix = value }

    var autoIncludeLocales: Boolean
        get() = state.autoIncludeLocales
        set(value) { state.autoIncludeLocales = value }

    var excludePreviewComposables: Boolean
        get() = state.excludePreviewComposables
        set(value) { state.excludePreviewComposables = value }

    var inspectionEnabled: Boolean
        get() = state.inspectionEnabled
        set(value) { state.inspectionEnabled = value }

    var ignoreLoggingStrings: Boolean
        get() = state.ignoreLoggingStrings
        set(value) { state.ignoreLoggingStrings = value }

    var detectFormatArgs: Boolean
        get() = state.detectFormatArgs
        set(value) { state.detectFormatArgs = value }

    var duplicateValueInspectionEnabled: Boolean
        get() = state.duplicateValueInspectionEnabled
        set(value) { state.duplicateValueInspectionEnabled = value }

    var unusedStringInspectionEnabled: Boolean
        get() = state.unusedStringInspectionEnabled
        set(value) { state.unusedStringInspectionEnabled = value }

    var namingConvention: NamingConvention
        get() = state.namingConvention
        set(value) { state.namingConvention = value }

    var maxKeyLength: Int
        get() = state.maxKeyLength
        set(value) { state.maxKeyLength = value }

    var minStringLength: Int
        get() = state.minStringLength
        set(value) { state.minStringLength = value }

    var sortAfterExtract: Boolean
        get() = state.sortAfterExtract
        set(value) { state.sortAfterExtract = value }

    var openStringsXmlAfterExtract: Boolean
        get() = state.openStringsXmlAfterExtract
        set(value) { state.openStringsXmlAfterExtract = value }

    var addSourceComment: Boolean
        get() = state.addSourceComment
        set(value) { state.addSourceComment = value }

    var trimWhitespace: Boolean
        get() = state.trimWhitespace
        set(value) { state.trimWhitespace = value }

    var excludePatterns: String
        get() = state.excludePatterns
        set(value) { state.excludePatterns = value }

    var activityStyle: ActivityReplacementStyle
        get() = state.activityStyle
        set(value) { state.activityStyle = value }

    var composeStyle: ComposeArgStyle
        get() = state.composeStyle
        set(value) { state.composeStyle = value }

    var lastTargetModulePath: String
        get() = if (state.rememberLastModule) state.lastTargetModulePath else ""
        set(value) { if (state.rememberLastModule) state.lastTargetModulePath = value }

    var rememberLastModule: Boolean
        get() = state.rememberLastModule
        set(value) { state.rememberLastModule = value }

    var customComposableLambdaFunctions: String
        get() = state.customComposableLambdaFunctions
        set(value) { state.customComposableLambdaFunctions = value }

    var cmpResPackageOverride: String
        get() = state.cmpResPackageOverride
        set(value) { state.cmpResPackageOverride = value }

    fun customComposableLambdaFunctionSet(): Set<String> =
        customComposableLambdaFunctions.split(',', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun excludePatternList(): List<Regex> {
        return excludePatterns.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
            .toList()
    }

    fun matchesExclude(value: String): Boolean =
        excludePatternList().any { it.matches(value) }

    companion object {
        fun getInstance(): StringSmithSettings =
            ApplicationManager.getApplication().getService(StringSmithSettings::class.java)
    }
}
