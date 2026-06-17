package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object DuplicateWriter {

    fun write(
        project: Project,
        source: DuplicateSource,
        result: DuplicateDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        var writeOk = true
        WriteCommandAction.runWriteCommandAction(project, "Duplicate String Resource", null, {
            writeOk = StringsXmlUtil.appendEntry(source.defaultFile, result.newKey, source.defaultValue, null, settings.sortAfterExtract)
            source.localeValues.forEach { (file, value) ->
                if (!StringsXmlUtil.keyExists(file, result.newKey)) {
                    StringsXmlUtil.appendEntry(file, result.newKey, value, null, settings.sortAfterExtract)
                }
            }
            if (result.updateReference && source.codeRef != null) {
                switchReference(source.codeRef, result.newKey, source.system)
            }
        })

        if (!writeOk) {
            StringSmithNotifications.warn(project, StringSmithBundle.message("write.error.noDocument", source.defaultFile.name))
        }
        if (settings.openStringsXmlAfterExtract) {
            jumpToEntry(project, source, result.newKey)
        }
    }

    private fun switchReference(ref: DuplicateCodeRef, newKey: String, system: ResourceSystem) {
        val doc = PsiDocumentManager.getInstance(ref.ktFile.project).getDocument(ref.ktFile) ?: return
        doc.replaceString(ref.keyRangeStart, ref.keyRangeEnd, newKey)
        PsiDocumentManager.getInstance(ref.ktFile.project).commitDocument(doc)
        if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            val resPkg = ref.ktFile.virtualFile?.let { CmpModuleUtil.findResPackage(ref.ktFile.project, it, ref.ktFile) }
            if (resPkg != null) {
                KtImportUtil.ensureImport(ref.ktFile, "$resPkg.$newKey")
                KtImportUtil.optimizeImports(ref.ktFile)
            }
        }
    }

    private fun jumpToEntry(project: Project, source: DuplicateSource, key: String) {
        val offset = StringsXmlUtil.offsetOfKey(source.defaultFile, key)
        if (offset >= 0) {
            FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, source.defaultFile, offset),
                true
            )
        }
    }
}
