package com.seijind.stringsmith.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/** Shared enable/dispatch wiring for the StringSmith editor actions. */
abstract class BaseExtractAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        // Shortcuts stay enabled anywhere in the file so a wrong caret position explains itself with an
        // error; the right-click menu only lists what applies at the caret.
        e.presentation.isEnabledAndVisible =
            project != null && editor != null && file != null && isAvailableFor(file) &&
                (e.place != ActionPlaces.EDITOR_POPUP || isApplicableAt(file, editor))
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        // A shortcut right after typing would otherwise read PSI offsets that no longer match the document.
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        perform(project, editor, file)
    }

    /** Hides the action in files where it can never apply. */
    protected abstract fun isAvailableFor(file: PsiFile): Boolean

    /** Cheap caret check for the context menu; actions working on the whole file keep the default. */
    protected open fun isApplicableAt(file: PsiFile, editor: Editor): Boolean = true

    protected abstract fun perform(project: Project, editor: Editor, file: PsiFile)
}
