package com.seijind.stringsmith.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.seijind.stringsmith.extract.ExtractContext
import com.seijind.stringsmith.extract.ExtractTarget
import com.seijind.stringsmith.extract.Replacement
import com.seijind.stringsmith.extract.StringsXmlUtil

class ExtractStringResourceAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val enabled = project != null && editor != null && file != null
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val target = ExtractContext.detect(file, editor)
        if (target == null) {
            Messages.showErrorDialog(project, "Place caret inside a string literal or XML attribute value.", "Extract String Resource")
            return
        }

        val stringsXml = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        if (stringsXml == null) {
            Messages.showErrorDialog(project, "No strings.xml found under res/values/.", "Extract String Resource")
            return
        }

        val existingKey = StringsXmlUtil.findExistingKey(stringsXml, target.rawValue)
        if (existingKey != null) {
            val reuse = Messages.showYesNoDialog(
                project,
                "String already exists with key \"$existingKey\". Reuse it?",
                "Extract String Resource",
                Messages.getQuestionIcon()
            )
            if (reuse == Messages.YES) {
                runReplaceOnly(project, editor, target, existingKey)
                return
            }
        }

        val suggested = suggestKey(target.rawValue)
        val key = promptForKey(project, suggested, stringsXml) ?: return

        runExtract(project, editor, target, stringsXml, key)
    }

    private fun runExtract(project: Project, editor: Editor, target: ExtractTarget, stringsXml: com.intellij.openapi.vfs.VirtualFile, key: String) {
        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            StringsXmlUtil.appendEntry(stringsXml, key, target.rawValue)
            Replacement.apply(editor, target, key)
        })
    }

    private fun runReplaceOnly(project: Project, editor: Editor, target: ExtractTarget, key: String) {
        WriteCommandAction.runWriteCommandAction(project, "Replace With String Resource", null, {
            Replacement.apply(editor, target, key)
        })
    }

    private fun promptForKey(project: Project, suggested: String, stringsXml: com.intellij.openapi.vfs.VirtualFile): String? {
        while (true) {
            val key = Messages.showInputDialog(
                project,
                "Resource key:",
                "Extract String Resource",
                Messages.getQuestionIcon(),
                suggested,
                null
            ) ?: return null
            if (!key.matches(KEY_PATTERN)) {
                Messages.showErrorDialog(project, "Invalid key. Use lowercase letters, digits, underscore. Must start with a letter.", "Extract String Resource")
                continue
            }
            if (StringsXmlUtil.keyExists(stringsXml, key)) {
                Messages.showErrorDialog(project, "Key \"$key\" already exists with a different value.", "Extract String Resource")
                continue
            }
            return key
        }
    }

    private fun suggestKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifEmpty { "label" }

    companion object {
        private val KEY_PATTERN = Regex("[a-z][a-z0-9_]*")
    }
}
