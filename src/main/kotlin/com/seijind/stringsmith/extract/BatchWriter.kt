package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile

object BatchWriter {

    private data class Edit(
        val start: Int,
        val end: Int,
        val replacementText: String,
        val key: String,
        val kind: ExtractContextKind
    )

    fun write(
        project: Project,
        editor: Editor,
        result: BatchDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        val included = result.rows.filter { it.include }
        if (included.isEmpty()) return

        val system = ResourceSystem.of(result.targetStringsXml)
        val ktFile = included.firstOrNull()?.target?.containingFile as? KtFile
        val androidRPackage = if (system == ResourceSystem.ANDROID) {
            ktFile?.virtualFile?.let { AndroidModuleUtil.findRPackage(it, ktFile) }
        } else null
        val cmpResPackage = if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            ktFile?.let { it.virtualFile?.let { vf -> CmpModuleUtil.findResPackage(it.project, vf, it) } }
        } else null

        val edits = buildEdits(included, system)
        val sourceFileName = included.firstOrNull()?.target?.containingFile?.name

        var failed: List<String> = emptyList()
        WriteCommandAction.runWriteCommandAction(project, "Batch Extract Strings", null, {
            val outcome = writeStringEntries(project, result, settings, sourceFileName)
            failed = outcome.failed
            // Only rewrite the editor (and imports) if the default file actually received the new keys;
            // otherwise the code would reference keys that were never written.
            if (outcome.defaultWritten) {
                applyEditorEdits(project, editor, edits)
                if (ktFile != null) addImports(ktFile, system, edits, androidRPackage, cmpResPackage)
            }
        })
        StringSmithNotifications.warnFailedWrites(project, failed)
    }

    /** Editor replacements for each included row, sorted last-to-first so offsets stay valid as we apply. */
    private fun buildEdits(included: List<BatchRow>, system: ResourceSystem): List<Edit> =
        included.mapNotNull { row ->
            val t = row.target
            val reference = Replacement.referenceFor(t, row.key, system)
            val (s, e, text) = when {
                t.kotlin != null -> Triple(t.kotlin.textRange.startOffset, t.kotlin.textRange.endOffset, reference)
                t.xml != null -> Triple(t.xml.textRange.startOffset, t.xml.textRange.endOffset, "\"$reference\"")
                else -> return@mapNotNull null
            }
            Edit(start = s, end = e, replacementText = text, key = row.key, kind = t.kind)
        }.sortedByDescending { it.start }

    /**
     * Adds new keys to the default file once, then mirrors them into each included locale.
     * Returns the project-relative paths of any files that could not be written (no editable document).
     */
    private data class WriteOutcome(val defaultWritten: Boolean, val failed: List<String>)

    private fun writeStringEntries(project: Project, result: BatchDialogResult, settings: StringSmithSettings, sourceFileName: String?): WriteOutcome {
        val addComment = settings.addSourceComment && sourceFileName != null
        // Set.add returns false for reuse rows, in-batch duplicates, and keys already present.
        val defaultExisting = StringsXmlUtil.readKeys(result.targetStringsXml).toMutableSet()
        val drafts = mutableListOf<StringEntryDraft>()
        result.rows.filter { it.include }.forEach { row ->
            if (row.existingKey != null && row.key == row.existingKey) return@forEach
            if (!defaultExisting.add(row.key)) return@forEach
            val comment = if (addComment) "from $sourceFileName:${row.sourceLine}" else null
            drafts += StringEntryDraft(row.key, row.value, comment)
        }
        val failed = mutableListOf<String>()
        val defaultWritten = StringsXmlUtil.appendEntries(result.targetStringsXml, drafts, settings.sortAfterExtract)
        if (!defaultWritten) {
            failed += DisplayPath.projectRelative(project, result.targetStringsXml)
            return WriteOutcome(defaultWritten = false, failed = failed)
        }
        result.localeSelections.filter { it.include }.forEach { loc ->
            val locExisting = StringsXmlUtil.readKeys(loc.file)
            if (!StringsXmlUtil.appendEntries(loc.file, drafts.filter { it.key !in locExisting }, settings.sortAfterExtract)) {
                failed += DisplayPath.projectRelative(project, loc.file)
            }
        }
        return WriteOutcome(defaultWritten = true, failed = failed)
    }

    private fun applyEditorEdits(project: Project, editor: Editor, edits: List<Edit>) {
        val doc = editor.document
        edits.forEach { doc.replaceString(it.start, it.end, it.replacementText) }
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }

    private fun addImports(
        ktFile: KtFile,
        system: ResourceSystem,
        edits: List<Edit>,
        androidRPackage: String?,
        cmpResPackage: String?
    ) = KtImportUtil.addResourceImports(
        file = ktFile,
        system = system,
        hasComposable = edits.any { it.kind == ExtractContextKind.COMPOSABLE },
        hasNonXmlReference = edits.any { it.kind != ExtractContextKind.XML_LAYOUT },
        keys = edits.map { it.key },
        androidRPackage = androidRPackage,
        cmpResPackage = cmpResPackage
    )
}
