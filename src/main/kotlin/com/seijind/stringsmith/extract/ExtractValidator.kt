package com.seijind.stringsmith.extract

import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractValidator {

    sealed class Result {
        data class Ok(val target: ExtractTarget) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun validate(target: ExtractTarget, settings: StringSmithSettings = StringSmithSettings.getInstance()): Result {
        val rawValue = if (settings.trimWhitespace) target.rawValue.trim() else target.rawValue
        if (rawValue.length < settings.minStringLength) {
            return Result.Rejected("String is shorter than the configured minimum (${settings.minStringLength}).")
        }
        if (settings.matchesExclude(rawValue)) {
            return Result.Rejected("String matches an exclusion pattern. Edit patterns in Settings → Tools → StringSmith.")
        }
        val effective = if (rawValue != target.rawValue) target.copy(rawValue = rawValue) else target
        return Result.Ok(effective)
    }
}
