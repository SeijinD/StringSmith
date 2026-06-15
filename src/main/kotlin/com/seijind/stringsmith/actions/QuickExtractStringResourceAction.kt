package com.seijind.stringsmith.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.ExtractRunner

class QuickExtractStringResourceAction : BaseExtractAction() {
    override fun perform(project: Project, editor: Editor, file: PsiFile) =
        ExtractRunner.runQuick(project, editor, file)
}
