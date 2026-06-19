package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

object EditWriter {

    fun write(
        project: Project,
        source: DuplicateSource,
        result: EditDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        val failed = mutableListOf<String>()
        val keyChanged = result.newKey != result.originalKey
        var renamedRefs = 0

        // Plan the reference rename before taking the write lock (see ReferenceRenamer.planRename).
        val renamePlan = if (keyChanged) {
            ReferenceRenamer.planRename(project, source.defaultFile, result.originalKey, result.newKey, source.system)
        } else null

        WriteCommandAction.runWriteCommandAction(project, "Edit String Resource", null, {
            val originalKey = result.originalKey

            // 1. Write all values under the ORIGINAL key first, so deletes/updates still find their entries.
            if (!StringsXmlUtil.updateValue(source.defaultFile, originalKey, result.newDefaultValue)) {
                failed += DisplayPath.projectRelative(project, source.defaultFile)
            }
            result.localeEdits.forEach { edit ->
                applyLocaleEdit(project, edit, originalKey, settings, failed)
            }

            // 2. Rename the key everywhere only after the values are in place.
            if (renamePlan != null) {
                StringsXmlUtil.renameKey(source.defaultFile, originalKey, result.newKey)
                result.localeEdits.forEach { StringsXmlUtil.renameKey(it.file, originalKey, result.newKey) }
                renamedRefs = ReferenceRenamer.applyPlan(project, renamePlan)
            }
        })

        StringSmithNotifications.warnFailedWrites(project, failed)
        // The rename touches code references in a separate, search-scoped pass that the user can't see; tell
        // them how many were updated so a missed reference (dynamic key, out-of-scope module) is visible
        // instead of surfacing later as a broken build.
        if (keyChanged && failed.isEmpty()) {
            val message = if (renamedRefs > 0) {
                StringSmithBundle.message("edit.renamed.withRefs", result.newKey, renamedRefs)
            } else {
                StringSmithBundle.message("edit.renamed.noRefs", result.newKey)
            }
            StringSmithNotifications.info(project, message)
        }
        if (settings.openStringsXmlAfterExtract) {
            EntryNavigation.openAtKey(project, source.defaultFile, result.newKey)
        }
    }

    private fun applyLocaleEdit(
        project: Project,
        edit: EditLocaleEdit,
        key: String,
        settings: StringSmithSettings,
        failed: MutableList<String>
    ) {
        val existed = edit.originalValue != null
        val newValue = edit.newValue
        val ok = when {
            newValue.isBlank() -> if (existed) StringsXmlUtil.deleteKey(edit.file, key) else true
            existed -> StringsXmlUtil.updateValue(edit.file, key, newValue)
            else -> StringsXmlUtil.appendEntry(edit.file, key, newValue, null, settings.sortAfterExtract)
        }
        if (!ok) failed += DisplayPath.projectRelative(project, edit.file)
    }
}
