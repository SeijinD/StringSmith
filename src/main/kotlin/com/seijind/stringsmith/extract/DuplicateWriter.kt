package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings

object DuplicateWriter {

    fun write(
        project: Project,
        source: DuplicateSource,
        result: DuplicateDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        val failed = mutableListOf<String>()
        val cmpResPackage = source.codeRef
            ?.takeIf { result.updateReference && source.system == ResourceSystem.COMPOSE_MULTIPLATFORM }
            ?.let { KtImportUtil.cmpResPackage(it.ktFile) }
        WriteCommandAction.runWriteCommandAction(project, "Duplicate String Resource", null, {
            // Copy the raw XML so markup (`<b>`), entities and Android quoting survive the copy unchanged.
            val defaultRaw = StringsXmlUtil.findRawValueOfKey(source.defaultFile, source.key)
            // Abort if the new key never lands in the default file: switching the caret reference to a
            // non-existent key would break the build with only a warning.
            if (defaultRaw == null || !StringsXmlUtil.appendEntry(
                    source.defaultFile, result.newKey, defaultRaw, null, settings.sortAfterExtract, isRawXml = true
                )
            ) {
                failed += DisplayPath.projectRelative(project, source.defaultFile)
                return@runWriteCommandAction
            }
            val localeRaws = source.localeValues.keys.mapNotNull { file ->
                StringsXmlUtil.findRawValueOfKey(file, source.key)?.let { file to it }
            }
            StringsXmlUtil.mirrorKeyToLocales(
                localeRaws, result.newKey, null, settings.sortAfterExtract, isRawXml = true
            ).forEach { failed += DisplayPath.projectRelative(project, it) }
            if (result.updateReference && source.codeRef != null) {
                switchReference(source.codeRef, result.newKey, cmpResPackage)
            }
        })

        StringSmithNotifications.warnFailedWrites(project, failed)
        if (settings.openStringsXmlAfterExtract) {
            EntryNavigation.openAtKey(project, source.defaultFile, result.newKey)
        }
    }

    private fun switchReference(ref: DuplicateCodeRef, newKey: String, cmpResPackage: String?) {
        val doc = PsiDocumentManager.getInstance(ref.ktFile.project).getDocument(ref.ktFile) ?: return
        doc.replaceString(ref.keyRangeStart, ref.keyRangeEnd, newKey)
        PsiDocumentManager.getInstance(ref.ktFile.project).commitDocument(doc)
        cmpResPackage?.let { KtImportUtil.addCmpKeyImport(ref.ktFile, it, newKey) }
    }

}
