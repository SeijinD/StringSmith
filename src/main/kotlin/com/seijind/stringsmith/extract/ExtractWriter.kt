package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object ExtractWriter {

    fun writeExtract(
        project: Project,
        editor: Editor,
        target: ExtractTarget,
        result: ExtractDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        val comment = if (settings.addSourceComment) buildSourceComment(target, editor) else null
        val system = ResourceSystem.of(result.targetStringsXml)

        var writeOk = true
        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            writeOk = StringsXmlUtil.appendEntry(result.targetStringsXml, result.key, result.defaultValue, comment, settings.sortAfterExtract)
            result.localeEntries.filter { it.include }.forEach { entry ->
                if (!StringsXmlUtil.keyExists(entry.file, result.key)) {
                    StringsXmlUtil.appendEntry(entry.file, result.key, entry.value, comment, settings.sortAfterExtract)
                }
            }
            Replacement.apply(editor, target, result.key, system)
        })

        if (!writeOk) {
            StringSmithNotifications.warn(project, StringSmithBundle.message("write.error.noDocument", result.targetStringsXml.name))
        }
        if (settings.openStringsXmlAfterExtract) {
            jumpToEntry(project, result.targetStringsXml, result.key)
        }
    }

    fun writeReplaceOnly(project: Project, editor: Editor, target: ExtractTarget, key: String, system: ResourceSystem) {
        WriteCommandAction.runWriteCommandAction(project, "Replace With String Resource", null, {
            Replacement.apply(editor, target, key, system)
        })
    }

    private fun jumpToEntry(project: Project, stringsXml: com.intellij.openapi.vfs.VirtualFile, key: String) {
        val offset = StringsXmlUtil.offsetOfKey(stringsXml, key)
        if (offset >= 0) {
            FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, stringsXml, offset),
                true
            )
        }
    }

    private fun buildSourceComment(target: ExtractTarget, editor: Editor): String {
        val fileName = target.containingFile.name
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        return "from $fileName:$lineNumber"
    }
}
