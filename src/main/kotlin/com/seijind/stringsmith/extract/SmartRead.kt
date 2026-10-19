package com.seijind.stringsmith.extract

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import java.util.concurrent.Callable

/** Index-backed lookups that must stay off the EDT and wait for indexing to finish. */
object SmartRead {

    /**
     * Runs [action] in a smart-mode non-blocking read action under a cancellable modal progress. Call from
     * the EDT. [action] may be restarted by a write action, so it should only compute and assign results.
     * Returns false when the user cancelled.
     */
    fun withProgress(project: Project, title: String, action: () -> Unit): Boolean =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            { ReadAction.nonBlocking(Callable { action() }).inSmartMode(project).executeSynchronously() },
            title,
            true,
            project
        )
}
