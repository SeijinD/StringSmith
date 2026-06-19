package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
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

        val failed = mutableListOf<String>()
        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            // If the key never lands in the default file, don't rewrite the editor to reference a key that
            // doesn't exist — that would silently break the build with only a warning.
            val defaultWritten = StringsXmlUtil.appendEntry(result.targetStringsXml, result.key, result.defaultValue, comment, settings.sortAfterExtract)
            if (!defaultWritten) {
                failed += DisplayPath.projectRelative(project, result.targetStringsXml)
                return@runWriteCommandAction
            }
            result.localeEntries.filter { it.include }.forEach { entry ->
                if (!StringsXmlUtil.keyExists(entry.file, result.key)) {
                    if (!StringsXmlUtil.appendEntry(entry.file, result.key, entry.value, comment, settings.sortAfterExtract)) {
                        failed += DisplayPath.projectRelative(project, entry.file)
                    }
                }
            }
            Replacement.apply(editor, target, result.key, system)
        })

        if (failed.isNotEmpty()) {
            StringSmithNotifications.warn(project, StringSmithBundle.message("write.error.noDocument", failed.joinToString(", ")))
        }
        if (settings.openStringsXmlAfterExtract) {
            EntryNavigation.openAtKey(project, result.targetStringsXml, result.key)
        }
    }

    fun writeReplaceOnly(project: Project, editor: Editor, target: ExtractTarget, key: String, system: ResourceSystem) {
        WriteCommandAction.runWriteCommandAction(project, "Replace With String Resource", null, {
            Replacement.apply(editor, target, key, system)
        })
    }

    private fun buildSourceComment(target: ExtractTarget, editor: Editor): String {
        val fileName = target.containingFile.name
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        return "from $fileName:$lineNumber"
    }
}
