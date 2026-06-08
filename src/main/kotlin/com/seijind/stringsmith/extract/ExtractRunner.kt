package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractRunner {

    fun run(project: Project, editor: Editor, file: PsiFile, settings: StringSmithSettings = StringSmithSettings.getInstance()) {
        val rawTarget = ExtractContext.detect(file, editor)
        if (rawTarget == null) {
            showError(project, StringSmithBundle.message("error.caret.notOnString"))
            return
        }

        val validation = ExtractValidator.validate(rawTarget, settings)
        if (validation is ExtractValidator.Result.Rejected) {
            showError(project, validation.reason)
            return
        }
        val target = (validation as ExtractValidator.Result.Ok).target

        val allTargets = StringsXmlUtil.findAllDefaultStringsXml(project)
        if (allTargets.isEmpty()) {
            showError(project, StringSmithBundle.message("error.noStringsXml"))
            return
        }

        val initialTarget = chooseInitialTarget(project, file, allTargets, settings)
        val existingKey = StringsXmlUtil.findExistingKey(initialTarget, target.rawValue)
        val suggested = KeyGenerator.suggest(target.rawValue, settings.keyPrefix, settings.namingConvention, settings.maxKeyLength)

        val dialog = ExtractDialog(
            project = project,
            rawValue = target.rawValue,
            target = target,
            suggestedKey = suggested,
            existingKey = existingKey,
            initialTarget = initialTarget,
            allTargets = allTargets
        )

        if (!dialog.showAndGet()) return
        val result = dialog.result()

        settings.lastTargetModulePath = result.targetStringsXml.path

        if (result.reuseExisting) {
            ExtractWriter.writeReplaceOnly(project, editor, target, result.key)
            return
        }

        val finalTarget = if (result.defaultValue != target.rawValue) target.copy(rawValue = result.defaultValue) else target
        ExtractWriter.writeExtract(project, editor, finalTarget, result, settings)
    }

    fun isExtractable(file: PsiFile, editor: Editor): Boolean =
        ExtractContext.detect(file, editor) != null

    private fun chooseInitialTarget(
        project: Project,
        file: PsiFile,
        allTargets: List<com.intellij.openapi.vfs.VirtualFile>,
        settings: StringSmithSettings
    ): com.intellij.openapi.vfs.VirtualFile {
        val remembered = settings.lastTargetModulePath
            .takeIf { it.isNotBlank() }
            ?.let { rem -> allTargets.firstOrNull { it.path == rem } }
        val nearest = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        return remembered ?: nearest ?: allTargets.first()
    }

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, StringSmithBundle.message("dialog.title"))
    }
}
