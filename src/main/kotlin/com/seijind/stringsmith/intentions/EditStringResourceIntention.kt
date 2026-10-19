package com.seijind.stringsmith.intentions

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.EditRunner

class EditStringResourceIntention : KeyRefIntention("edit.intention.text", AllIcons.Actions.Edit) {
    override fun run(project: Project, editor: Editor, file: PsiFile) = EditRunner.run(project, editor, file)
}
