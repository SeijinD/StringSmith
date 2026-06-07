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

        val stringsXml = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        if (stringsXml == null) {
            Messages.showErrorDialog(project, "No strings.xml found under res/values/.", DIALOG_TITLE)
            return
        }

        val existingKey = StringsXmlUtil.findExistingKey(stringsXml, effectiveTarget.rawValue)
        if (existingKey != null) {
            val reuse = Messages.showYesNoDialog(
                project,
                "String already exists with key \"$existingKey\". Reuse it?",
                DIALOG_TITLE,
                Messages.getQuestionIcon()
            )
            if (reuse == Messages.YES) {
                replaceOnly(project, editor, effectiveTarget, existingKey)
                return
            }
        }

        val suggested = suggestKey(effectiveTarget.rawValue)
        val key = promptForKey(project, suggested, stringsXml) ?: return

        runExtract(project, editor, effectiveTarget, stringsXml, key)
    }

    fun isExtractable(file: PsiFile, editor: Editor): Boolean =
        ExtractContext.detect(file, editor) != null

    private fun runExtract(project: Project, editor: Editor, target: ExtractTarget, stringsXml: VirtualFile, key: String) {
        val settings = StringSmithSettings.getInstance()
        val variants = StringsXmlUtil.findLocaleVariants(stringsXml)
        val propagate = variants.isNotEmpty() && shouldPropagate(project, variants)
        val comment = if (settings.addSourceComment) buildSourceComment(target, editor) else null

        WriteCommandAction.runWriteCommandAction(project, "Extract String Resource", null, {
            StringsXmlUtil.appendEntry(stringsXml, key, target.rawValue, comment, settings.sortAfterExtract)
            if (propagate) {
                variants.forEach { variant ->
                    if (!StringsXmlUtil.keyExists(variant, key)) {
                        StringsXmlUtil.appendEntry(variant, key, target.rawValue, comment, settings.sortAfterExtract)
                    }
                }
            }
            Replacement.apply(editor, target, key)
        })

        if (settings.openStringsXmlAfterExtract) {
            val offset = StringsXmlUtil.offsetOfKey(stringsXml, key)
            if (offset >= 0) {
                FileEditorManager.getInstance(project).openTextEditor(
                    OpenFileDescriptor(project, stringsXml, offset),
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

    private fun shouldPropagate(project: Project, variants: List<VirtualFile>): Boolean {
        return when (StringSmithSettings.getInstance().localePropagation) {
            LocalePropagation.ALWAYS -> true
            LocalePropagation.NEVER -> false
            LocalePropagation.ASK -> askPropagate(project, variants)
        }
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
            if (!KeyGenerator.isValidKey(key)) {
                Messages.showErrorDialog(project, "Invalid key. Use letters, digits, underscore. Must start with a letter.", DIALOG_TITLE)
                continue
            }
            if (StringsXmlUtil.keyExists(stringsXml, key)) {
                Messages.showErrorDialog(project, "Key \"$key\" already exists with a different value.", DIALOG_TITLE)
                continue
            }
            return key
        }
    }

    private fun suggestKey(value: String): String {
        val s = StringSmithSettings.getInstance()
        return KeyGenerator.suggest(value, s.keyPrefix, s.namingConvention, s.maxKeyLength)
    }

    private const val DIALOG_TITLE = "Extract String Resource"
}
