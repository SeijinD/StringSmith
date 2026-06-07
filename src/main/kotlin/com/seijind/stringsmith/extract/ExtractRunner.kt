package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object ExtractRunner {

    fun run(project: Project, editor: Editor, file: PsiFile) {
        val target = ExtractContext.detect(file, editor)
        if (target == null) {
            Messages.showErrorDialog(project, "Place caret inside a string literal or XML attribute value.", DIALOG_TITLE)
            return
        }

        val stringsXml = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        if (stringsXml == null) {
            Messages.showErrorDialog(project, "No strings.xml found under res/values/.", DIALOG_TITLE)
            return
        }

        val existingKey = StringsXmlUtil.findExistingKey(stringsXml, target.rawValue)
        if (existingKey != null) {
            val reuse = Messages.showYesNoDialog(
                project,
                "String already exists with key \"$existingKey\". Reuse it?",
                DIALOG_TITLE,
                Messages.getQuestionIcon()
            )
            if (reuse == Messages.YES) {
                replaceOnly(project, editor, target, existingKey)
                return
            }
        }

        val suggested = suggestKey(target.rawValue)
        val key = promptForKey(project, suggested, stringsXml) ?: return

        runExtract(project, editor, target, stringsXml, key)
    }

    fun isExtractable(file: PsiFile, editor: Editor): Boolean =
        ExtractContext.detect(file, editor) != null

    private fun runExtract(project: Project, editor: Editor, target: ExtractTarget, stringsXml: VirtualFile, key: String) {
        val variants = StringsXmlUtil.findLocaleVariants(stringsXml)
        val propagate = variants.isNotEmpty() && askPropagate(project, variants)
        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            StringsXmlUtil.appendEntry(stringsXml, key, target.rawValue)
            if (propagate) {
                variants.forEach { variant ->
                    if (!StringsXmlUtil.keyExists(variant, key)) {
                        StringsXmlUtil.appendEntry(variant, key, target.rawValue)
                    }
                }
            }
            Replacement.apply(editor, target, key)
        })
    }

    private fun replaceOnly(project: Project, editor: Editor, target: ExtractTarget, key: String) {
        WriteCommandAction.runWriteCommandAction(project, "Replace With String Resource", null, {
            Replacement.apply(editor, target, key)
        })
    }

    private fun askPropagate(project: Project, variants: List<VirtualFile>): Boolean {
        val names = variants.mapNotNull { it.parent?.name }.joinToString(", ")
        val answer = Messages.showYesNoDialog(
            project,
            "Also add placeholder entry to ${variants.size} locale file(s)?\n$names",
            DIALOG_TITLE,
            Messages.getQuestionIcon()
        )
        return answer == Messages.YES
    }

    private fun promptForKey(project: Project, suggested: String, stringsXml: VirtualFile): String? {
        while (true) {
            val key = Messages.showInputDialog(
                project,
                "Resource key:",
                DIALOG_TITLE,
                Messages.getQuestionIcon(),
                suggested,
                null
            ) ?: return null
            if (!key.matches(KEY_PATTERN)) {
                Messages.showErrorDialog(project, "Invalid key. Use lowercase letters, digits, underscore. Must start with a letter.", DIALOG_TITLE)
                continue
            }
            if (StringsXmlUtil.keyExists(stringsXml, key)) {
                Messages.showErrorDialog(project, "Key \"$key\" already exists with a different value.", DIALOG_TITLE)
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

    private val KEY_PATTERN = Regex("[a-z][a-z0-9_]*")
    private const val DIALOG_TITLE = "Extract String Resource"
}
