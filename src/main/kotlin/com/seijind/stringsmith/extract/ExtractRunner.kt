package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.seijind.stringsmith.settings.LocalePropagation
import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractRunner {

    fun run(project: Project, editor: Editor, file: PsiFile) {
        val settings = StringSmithSettings.getInstance()
        val target = ExtractContext.detect(file, editor)
        if (target == null) {
            Messages.showErrorDialog(project, "Place caret inside a string literal or XML attribute value.", DIALOG_TITLE)
            return
        }

        val rawValue = if (settings.trimWhitespace) target.rawValue.trim() else target.rawValue
        if (rawValue.length < settings.minStringLength) {
            Messages.showErrorDialog(project, "String is shorter than the configured minimum (${settings.minStringLength}).", DIALOG_TITLE)
            return
        }
        if (settings.matchesExclude(rawValue)) {
            Messages.showErrorDialog(project, "String matches an exclusion pattern. Edit patterns in Settings → Tools → StringSmith.", DIALOG_TITLE)
            return
        }

        val effectiveTarget = if (rawValue != target.rawValue) target.copy(rawValue = rawValue) else target

        val allTargets = StringsXmlUtil.findAllDefaultStringsXml(project)
        if (allTargets.isEmpty()) {
            Messages.showErrorDialog(project, "No strings.xml found under res/values/.", DIALOG_TITLE)
            return
        }

        val remembered = settings.lastTargetModulePath
            .takeIf { it.isNotBlank() }
            ?.let { rem -> allTargets.firstOrNull { it.path == rem } }
        val nearest = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        val initialTarget = remembered ?: nearest ?: allTargets.first()

        val existingKey = StringsXmlUtil.findExistingKey(initialTarget, effectiveTarget.rawValue)
        val suggested = suggestKey(effectiveTarget.rawValue)

        val dialog = ExtractDialog(
            project = project,
            rawValue = effectiveTarget.rawValue,
            target = effectiveTarget,
            suggestedKey = suggested,
            existingKey = existingKey,
            initialTarget = initialTarget,
            allTargets = allTargets
        )

        if (!dialog.showAndGet()) return
        val result = dialog.result()

        settings.lastTargetModulePath = result.targetStringsXml.path

        if (result.reuseExisting) {
            replaceOnly(project, editor, effectiveTarget, result.key)
            return
        }

        val finalTarget = if (result.defaultValue != effectiveTarget.rawValue) {
            effectiveTarget.copy(rawValue = result.defaultValue)
        } else effectiveTarget

        runExtract(project, editor, finalTarget, result)
    }

    fun isExtractable(file: PsiFile, editor: Editor): Boolean =
        ExtractContext.detect(file, editor) != null

    private fun runExtract(project: Project, editor: Editor, target: ExtractTarget, result: ExtractDialogResult) {
        val settings = StringSmithSettings.getInstance()
        val comment = if (settings.addSourceComment) buildSourceComment(target, editor) else null
        val applyPropagation = settings.localePropagation != LocalePropagation.NEVER

        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            StringsXmlUtil.appendEntry(result.targetStringsXml, result.key, result.defaultValue, comment, settings.sortAfterExtract)
            if (applyPropagation) {
                result.localeEntries.filter { it.include }.forEach { entry ->
                    if (!StringsXmlUtil.keyExists(entry.file, result.key)) {
                        StringsXmlUtil.appendEntry(entry.file, result.key, entry.value, comment, settings.sortAfterExtract)
                    }
                }
            }
            Replacement.apply(editor, target, result.key)
        })

        if (settings.openStringsXmlAfterExtract) {
            val offset = StringsXmlUtil.offsetOfKey(result.targetStringsXml, result.key)
            if (offset >= 0) {
                FileEditorManager.getInstance(project).openTextEditor(
                    OpenFileDescriptor(project, result.targetStringsXml, offset),
                    true
                )
            }
        }
    }

    private fun buildSourceComment(target: ExtractTarget, editor: Editor): String {
        val fileName = target.containingFile.name
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        return "from $fileName:$lineNumber"
    }

    private fun replaceOnly(project: Project, editor: Editor, target: ExtractTarget, key: String) {
        WriteCommandAction.runWriteCommandAction(project, "Replace With String Resource", null, {
            Replacement.apply(editor, target, key)
        })
    }

    private fun suggestKey(value: String): String {
        val s = StringSmithSettings.getInstance()
        return KeyGenerator.suggest(value, s.keyPrefix, s.namingConvention, s.maxKeyLength)
    }

    private const val DIALOG_TITLE = "Extract String Resource"
}
