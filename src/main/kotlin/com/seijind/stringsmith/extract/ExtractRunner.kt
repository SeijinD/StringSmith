package com.seijind.stringsmith.extract

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractRunner {

    /** Common context computed once for both the full and the quick flow. */
    private data class Prepared(
        val target: ExtractTarget,
        val allTargets: List<VirtualFile>,
        val initialTarget: VirtualFile,
        val existingKey: String?,
        val suggested: String
    )

    fun run(project: Project, editor: Editor, file: PsiFile, settings: StringSmithSettings = StringSmithSettings.getInstance()) {
        val prepared = prepare(project, editor, file, settings) ?: return
        runWithDialog(project, editor, prepared, settings)
    }

    fun runQuick(project: Project, editor: Editor, file: PsiFile, settings: StringSmithSettings = StringSmithSettings.getInstance()) {
        val prepared = prepare(project, editor, file, settings) ?: return

        val variants = StringsXmlUtil.findLocaleVariants(prepared.initialTarget)
        val collision = StringsXmlUtil.keyExists(prepared.initialTarget, prepared.suggested)
        val canSkip = prepared.allTargets.size == 1 &&
            prepared.existingKey == null &&
            !collision &&
            variants.isEmpty() &&
            KeyGenerator.isValidKey(prepared.suggested)

        if (!canSkip) {
            runWithDialog(project, editor, prepared, settings)
            return
        }

        settings.lastTargetModulePath = prepared.initialTarget.path
        val result = ExtractDialogResult(
            key = prepared.suggested,
            reuseExisting = false,
            targetStringsXml = prepared.initialTarget,
            defaultValue = prepared.target.rawValue,
            localeEntries = emptyList()
        )
        ExtractWriter.writeExtract(project, editor, prepared.target, result, settings)
        // Quick path skips the dialog, so surface the chosen key inline instead of silently changing the code.
        HintManager.getInstance().showInformationHint(editor, StringSmithBundle.message("quick.extracted", prepared.suggested))
    }

    fun isExtractable(file: PsiFile, editor: Editor): Boolean =
        ExtractContext.detect(file, editor) != null

    /** Detect + validate + resolve targets. Reports the error and returns null when not extractable. */
    private fun prepare(project: Project, editor: Editor, file: PsiFile, settings: StringSmithSettings): Prepared? {
        val rawTarget = ExtractContext.detect(file, editor)
        if (rawTarget == null) {
            showError(project, StringSmithBundle.message("error.caret.notOnString"))
            return null
        }

        val validation = ExtractValidator.validate(rawTarget, settings)
        if (validation is ExtractValidator.Result.Rejected) {
            showError(project, validation.reason)
            return null
        }
        val target = (validation as ExtractValidator.Result.Ok).target

        val allTargets = StringsXmlUtil.findAllDefaultStringsXml(project)
        if (allTargets.isEmpty()) {
            showError(project, StringSmithBundle.message("error.noStringsXml"))
            return null
        }

        val initialTarget = ModuleResolver.chooseInitialTarget(project, file, allTargets, settings)
        val existingKey = StringsXmlUtil.findExistingKey(initialTarget, target.rawValue)
        val suggested = KeyGenerator.suggest(target.rawValue, settings.keyPrefix, settings.namingConvention, settings.maxKeyLength)
        return Prepared(target, allTargets, initialTarget, existingKey, suggested)
    }

    private fun runWithDialog(project: Project, editor: Editor, prepared: Prepared, settings: StringSmithSettings) {
        val dialog = ExtractDialog(
            project = project,
            rawValue = prepared.target.rawValue,
            target = prepared.target,
            suggestedKey = prepared.suggested,
            existingKey = prepared.existingKey,
            initialTarget = prepared.initialTarget,
            allTargets = prepared.allTargets
        )

        if (!dialog.showAndGet()) return
        val result = dialog.result()

        settings.lastTargetModulePath = result.targetStringsXml.path

        if (result.reuseExisting) {
            ExtractWriter.writeReplaceOnly(project, editor, prepared.target, result.key, ResourceSystem.of(result.targetStringsXml))
            return
        }

        val finalTarget = if (result.defaultValue != prepared.target.rawValue) {
            prepared.target.copy(rawValue = result.defaultValue)
        } else {
            prepared.target
        }
        ExtractWriter.writeExtract(project, editor, finalTarget, result, settings)
    }

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, StringSmithBundle.message("dialog.title"))
    }
}
