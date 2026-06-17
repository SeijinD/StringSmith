package com.seijind.stringsmith.extract

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** User-facing balloons for write failures that would otherwise pass silently. */
object StringSmithNotifications {

    private const val GROUP_ID = "StringSmith"

    fun warn(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            ?.createNotification(message, NotificationType.WARNING)
            ?.notify(project)
    }
}
