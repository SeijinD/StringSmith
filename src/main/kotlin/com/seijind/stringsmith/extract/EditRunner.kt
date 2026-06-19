package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle

object EditRunner {

    fun isEditable(project: Project, file: PsiFile, editor: Editor): Boolean =
        DuplicateContext.detect(project, file, editor) != null

    fun run(project: Project, editor: Editor, file: PsiFile) {
        val source = DuplicateContext.detect(project, file, editor)
        if (source == null) {
            Messages.showErrorDialog(
                project,
                StringSmithBundle.message("error.edit.notOnKey"),
                StringSmithBundle.message("edit.dialog.title")
            )
            return
        }

        val dialog = EditDialog(project, source)
        if (!dialog.showAndGet()) return
        EditWriter.write(project, source, dialog.result())
    }
}
