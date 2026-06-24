package com.seijind.stringsmith.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.DuplicateRunner
import javax.swing.Icon

/** Alt+Enter: duplicate the string resource referenced under the caret (R.string / Res.string or a <string> entry). */
class DuplicateStringResourceIntention : IntentionAction, PriorityAction, Iconable {

    override fun getText(): String = StringSmithBundle.message("duplicate.intention.text")

    override fun getFamilyName(): String = "StringSmith"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.Copy

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        return DuplicateRunner.isDuplicatable(file, editor)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        DuplicateRunner.run(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = false
}
