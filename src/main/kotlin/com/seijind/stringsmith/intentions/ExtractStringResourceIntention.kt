package com.seijind.stringsmith.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.ExtractRunner

class ExtractStringResourceIntention : IntentionAction, PriorityAction {

    override fun getText(): String = "Extract to strings.xml"

    override fun getFamilyName(): String = "StringSmith"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        return ExtractRunner.isExtractable(file, editor)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        ExtractRunner.run(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = false
}
