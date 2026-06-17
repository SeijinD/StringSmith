package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object BatchRunner {

    fun run(project: Project, editor: Editor, file: PsiFile, settings: StringSmithSettings = StringSmithSettings.getInstance()) {
        val candidates = BatchScanner.scan(file, settings)
        if (candidates.isEmpty()) {
            showError(project, StringSmithBundle.message("batch.error.noCandidates"))
            return
        }

        val allTargets = StringsXmlUtil.findAllDefaultStringsXml(project)
        if (allTargets.isEmpty()) {
            showError(project, StringSmithBundle.message("error.noStringsXml"))
            return
        }

        val initialTarget = chooseInitialTarget(project, file, allTargets, settings)

        // Seed each row with a suggested key only; BatchDialog recomputes existing-key reuse and status
        // for the chosen target in its init (refreshAllStatuses), so duplicating that here is redundant.
        val rows = candidates.map { target ->
            BatchRow(
                target = target,
                include = true,
                key = KeyGenerator.suggest(target.rawValue, settings.keyPrefix, settings.namingConvention, settings.maxKeyLength),
                value = target.rawValue,
                sourceLine = lineOf(editor, target),
                existingKey = null,
                status = BatchRowStatus.NEW
            )
        }

        val dialog = BatchDialog(project, rows, initialTarget, allTargets)
        if (!dialog.showAndGet()) return

        val result = dialog.result()
        settings.lastTargetModulePath = result.targetStringsXml.path
        BatchWriter.write(project, editor, result, settings)
    }

    private fun lineOf(editor: Editor, target: ExtractTarget): Int {
        val offset = BatchScanner.startOffsetOf(target)
        if (offset < 0) return 0
        return editor.document.getLineNumber(offset) + 1
    }

    private fun chooseInitialTarget(
        project: Project,
        file: PsiFile,
        allTargets: List<com.intellij.openapi.vfs.VirtualFile>,
        settings: StringSmithSettings
    ): com.intellij.openapi.vfs.VirtualFile =
        ModuleResolver.chooseInitialTarget(project, file, allTargets, settings)

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, StringSmithBundle.message("batch.dialog.title"))
    }
}
