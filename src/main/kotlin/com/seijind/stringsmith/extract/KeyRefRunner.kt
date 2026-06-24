package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle

/**
 * Shared detect-then-act flow for actions that operate on the string reference under the caret
 * (Duplicate, Edit). Both resolve the same [DuplicateSource]; only the dialog/writer and the
 * not-on-key error messages differ, which the caller supplies.
 */
object KeyRefRunner {

    // Cheap syntactic check only — the project scan/parse happens in [run], not on every Alt+Enter.
    fun isAvailable(file: PsiFile, editor: Editor): Boolean =
        DuplicateContext.isOnResourceRef(file, editor)

    fun run(
        project: Project,
        file: PsiFile,
        editor: Editor,
        errorKey: String,
        titleKey: String,
        onSource: (DuplicateSource) -> Unit
    ) {
        val source = DuplicateContext.detect(project, file, editor)
        if (source == null) {
            Messages.showErrorDialog(
                project,
                StringSmithBundle.message(errorKey),
                StringSmithBundle.message(titleKey)
            )
            return
        }
        onSource(source)
    }
}
