package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle

object DuplicateRunner {

    fun isDuplicatable(project: Project, file: PsiFile, editor: Editor): Boolean =
        DuplicateContext.detect(project, file, editor) != null

    fun run(project: Project, editor: Editor, file: PsiFile) {
        val source = DuplicateContext.detect(project, file, editor)
        if (source == null) {
            Messages.showErrorDialog(
                project,
                StringSmithBundle.message("error.duplicate.notOnKey"),
                StringSmithBundle.message("duplicate.dialog.title")
            )
            return
        }

        val dialog = DuplicateDialog(project, source)
        if (!dialog.showAndGet()) return
        DuplicateWriter.write(project, source, dialog.result())
    }
}
