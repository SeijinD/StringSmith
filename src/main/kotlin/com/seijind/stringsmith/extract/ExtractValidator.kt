package com.seijind.stringsmith.extract

import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractValidator {

    sealed class Result {
        data class Ok(val target: ExtractTarget) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun validate(target: ExtractTarget, settings: StringSmithSettings = StringSmithSettings.getInstance()): Result {
        val rawValue = if (settings.trimWhitespace) target.rawValue.trim() else target.rawValue
        if (rawValue.length < settings.minStringLength) {
            return Result.Rejected(StringSmithBundle.message("error.tooShort", settings.minStringLength))
        }
        if (settings.matchesExclude(rawValue)) {
            return Result.Rejected(StringSmithBundle.message("error.matchesExclusion"))
        }
        if (settings.excludePreviewComposables && ExtractContext.isInsidePreviewComposable(target)) {
            return Result.Rejected(StringSmithBundle.message("error.insidePreview"))
        }
        val effective = if (rawValue != target.rawValue) target.copy(rawValue = rawValue) else target
        return Result.Ok(effective)
    }
}
