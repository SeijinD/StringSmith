package com.seijind.stringsmith.extract

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.seijind.stringsmith.StringSmithBundle

/** User-facing balloons for write failures that would otherwise pass silently. */
object StringSmithNotifications {

    private const val GROUP_ID = "StringSmith"

    /** Warns about files that could not be written (no editable document); no-op when [failed] is empty. */
    fun warnFailedWrites(project: Project, failed: List<String>) {
        if (failed.isEmpty()) return
        notify(project, NotificationType.WARNING, StringSmithBundle.message("write.error.noDocument", failed.joinToString(", ")))
    }

    fun info(project: Project, message: String) = notify(project, NotificationType.INFORMATION, message)

    private fun notify(project: Project, type: NotificationType, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            ?.createNotification(message, type)
            ?.notify(project)
    }
}
