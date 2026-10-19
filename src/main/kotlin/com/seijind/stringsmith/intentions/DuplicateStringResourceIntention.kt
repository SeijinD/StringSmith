package com.seijind.stringsmith.intentions

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.DuplicateRunner

class DuplicateStringResourceIntention : KeyRefIntention("duplicate.intention.text", AllIcons.Actions.Copy) {
    override fun run(project: Project, editor: Editor, file: PsiFile) = DuplicateRunner.run(project, editor, file)
}
