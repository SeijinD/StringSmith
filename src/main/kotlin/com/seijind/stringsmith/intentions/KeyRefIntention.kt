package com.seijind.stringsmith.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.DuplicateContext
import javax.swing.Icon

/** Alt+Enter on a string resource reference (R.string / Res.string or a <string> entry). */
abstract class KeyRefIntention(private val textKey: String, private val icon: Icon) : IntentionAction, PriorityAction, Iconable {

    override fun getText(): String = StringSmithBundle.message(textKey)

    override fun getFamilyName(): String = "StringSmith"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int): Icon = icon

    // Cheap syntactic check only; the project scan happens in invoke, not on every Alt+Enter.
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        return DuplicateContext.isOnResourceRef(file, editor)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        run(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = false

    protected abstract fun run(project: Project, editor: Editor, file: PsiFile)
}
