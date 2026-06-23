package com.seijind.stringsmith.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.extract.BatchRunner
import com.seijind.stringsmith.extract.ExtractContext

class BatchExtractStringResourceAction : BaseExtractAction() {
    override fun isAvailableFor(file: PsiFile): Boolean = ExtractContext.isExtractEligibleFile(file)

    override fun perform(project: Project, editor: Editor, file: PsiFile) =
        BatchRunner.run(project, editor, file)
}
