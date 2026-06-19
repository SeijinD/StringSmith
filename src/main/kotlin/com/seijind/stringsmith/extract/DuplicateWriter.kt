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
        WriteCommandAction.runWriteCommandAction(project, "Duplicate String Resource", null, {
            // Abort if the new key never lands in the default file: switching the caret reference to a
            // non-existent key would break the build with only a warning.
            if (!StringsXmlUtil.appendEntry(source.defaultFile, result.newKey, source.defaultValue, null, settings.sortAfterExtract)) {
                failed += DisplayPath.projectRelative(project, source.defaultFile)
                return@runWriteCommandAction
            }
            StringsXmlUtil.mirrorKeyToLocales(
                source.localeValues.toList(), result.newKey, null, settings.sortAfterExtract
            ).forEach { failed += DisplayPath.projectRelative(project, it) }
            if (result.updateReference && source.codeRef != null) {
                switchReference(source.codeRef, result.newKey, source.system)
            }
        })

        StringSmithNotifications.warnFailedWrites(project, failed)
        if (settings.openStringsXmlAfterExtract) {
            EntryNavigation.openAtKey(project, source.defaultFile, result.newKey)
        }
    }

    private fun switchReference(ref: DuplicateCodeRef, newKey: String, system: ResourceSystem) {
        val doc = PsiDocumentManager.getInstance(ref.ktFile.project).getDocument(ref.ktFile) ?: return
        doc.replaceString(ref.keyRangeStart, ref.keyRangeEnd, newKey)
        PsiDocumentManager.getInstance(ref.ktFile.project).commitDocument(doc)
        if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            KtImportUtil.addCmpKeyImport(ref.ktFile.project, ref.ktFile, newKey)
        }
    }

}
