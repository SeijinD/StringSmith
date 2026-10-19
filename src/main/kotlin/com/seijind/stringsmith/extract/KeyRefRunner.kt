package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle

/**
 * Shared detect-then-act flow for actions that operate on the string reference under the caret
 * (Duplicate, Edit). Both resolve the same [DuplicateSource]; only the dialog/writer differs.
 */
object KeyRefRunner {

    fun run(
        project: Project,
        file: PsiFile,
        editor: Editor,
        titleKey: String,
        onSource: (DuplicateSource) -> Unit
    ) {
        val source = DuplicateContext.detect(project, file, editor)
        if (source == null) {
            Messages.showErrorDialog(
                project,
                StringSmithBundle.message("error.notOnKey"),
                StringSmithBundle.message(titleKey)
            )
            return
        }
        onSource(source)
    }
}
