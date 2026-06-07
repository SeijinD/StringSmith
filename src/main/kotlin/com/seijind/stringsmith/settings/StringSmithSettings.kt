package com.seijind.stringsmith.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

enum class LocalePropagation(val display: String) {
    ALWAYS("Always propagate"),
    NEVER("Default file only"),
    ASK("Ask each time");
    override fun toString(): String = display
}

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
        var localePropagation: LocalePropagation = LocalePropagation.ASK,
        var namingConvention: NamingConvention = NamingConvention.SNAKE_CASE,
        var maxKeyLength: Int = 40,
        var minStringLength: Int = 2,
        var sortAfterExtract: Boolean = false,
        var openStringsXmlAfterExtract: Boolean = false,
        var addSourceComment: Boolean = false,
        var trimWhitespace: Boolean = true,
        var excludePatterns: String = "^[A-Z_]{2,}$\nhttps?://.*",
        var activityStyle: ActivityReplacementStyle = ActivityReplacementStyle.GET_STRING,
        var composeStyle: ComposeArgStyle = ComposeArgStyle.POSITIONAL
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

    var localePropagation: LocalePropagation
        get() = state.localePropagation
        set(value) { state.localePropagation = value }

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
