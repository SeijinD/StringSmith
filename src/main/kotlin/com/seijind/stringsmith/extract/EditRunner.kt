package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

object EditRunner {

    fun isEditable(file: PsiFile, editor: Editor): Boolean =
        KeyRefRunner.isAvailable(file, editor)

    fun run(project: Project, editor: Editor, file: PsiFile) =
        KeyRefRunner.run(project, file, editor, "error.edit.notOnKey", "edit.dialog.title") { source ->
            val dialog = EditDialog(project, source)
            if (dialog.showAndGet()) EditWriter.write(project, source, dialog.result())
        }
}
