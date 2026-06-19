package com.seijind.stringsmith.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.EditRunner
import javax.swing.Icon

/** Alt+Enter: edit the string resource referenced under the caret (R.string / Res.string or a <string> entry). */
class EditStringResourceIntention : IntentionAction, PriorityAction, Iconable {

    override fun getText(): String = StringSmithBundle.message("edit.intention.text")

    override fun getFamilyName(): String = "StringSmith"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.Edit

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        return EditRunner.isEditable(project, file, editor)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        EditRunner.run(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = false
}
