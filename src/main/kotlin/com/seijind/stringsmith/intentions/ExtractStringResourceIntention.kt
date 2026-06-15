package com.seijind.stringsmith.intentions

import com.seijind.stringsmith.settings.StringSmithSettings

/** Registered intention; suppressed when the hardcoded-string inspection is on (which provides its own quick-fix). */
class ExtractStringResourceIntention : BaseExtractIntention() {
    override fun isEnabled(): Boolean = !StringSmithSettings.getInstance().inspectionEnabled
}
