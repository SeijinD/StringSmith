package com.seijind.stringsmith.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.ExtractRunner
import com.seijind.stringsmith.settings.StringSmithSettings
import javax.swing.Icon

class ExtractStringResourceIntention : IntentionAction, PriorityAction, Iconable {

    override fun getText(): String = "Extract to strings.xml"

    override fun getFamilyName(): String = "StringSmith"

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (StringSmithSettings.getInstance().inspectionEnabled) return false
        return ExtractRunner.isExtractable(file, editor)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        ExtractRunner.run(project, editor, file)
    }

    override fun startInWriteAction(): Boolean = false
}
