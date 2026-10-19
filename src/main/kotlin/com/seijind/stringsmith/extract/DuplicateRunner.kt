package com.seijind.stringsmith.extract

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle

object DuplicateRunner {

    fun run(project: Project, editor: Editor, file: PsiFile) =
        KeyRefRunner.run(project, file, editor, "error.duplicate.notOnKey", "duplicate.dialog.title") { source ->
            val dialog = DuplicateDialog(project, source)
            if (dialog.showAndGet()) {
                val result = dialog.result()
                DuplicateWriter.write(project, source, result)
                if (result.updateReference && source.codeRef != null) {
                    HintManager.getInstance().showInformationHint(editor, StringSmithBundle.message("duplicate.redirected", result.newKey))
                }
            }
        }
}
