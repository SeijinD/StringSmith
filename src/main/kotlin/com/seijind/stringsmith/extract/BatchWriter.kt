package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile

object BatchWriter {

    fun write(
        project: Project,
        editor: Editor,
        result: BatchDialogResult,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ) {
        val included = result.rows.filter { it.include }
        if (included.isEmpty()) return

        data class Edit(
            val start: Int,
            val end: Int,
            val replacementText: String,
            val key: String,
            val kind: ExtractContextKind
        )

        val system = ResourceSystem.of(result.targetStringsXml)
        val ktFile = included.firstOrNull()?.target?.containingFile as? KtFile
        val androidRPackage = if (system == ResourceSystem.ANDROID) {
            ktFile?.virtualFile?.let { AndroidModuleUtil.findRPackage(it, ktFile) }
        } else null
        val cmpResPackage = if (system == ResourceSystem.COMPOSE_MULTIPLATFORM) {
            ktFile?.let { it.virtualFile?.let { vf -> CmpModuleUtil.findResPackage(it.project, vf, it) } }
        } else null

        val edits = included.mapNotNull { row ->
            val t = row.target
            val reference = Replacement.referenceFor(t, row.key, system)
            val (s, e, text) = when {
                t.kotlin != null -> Triple(t.kotlin.textRange.startOffset, t.kotlin.textRange.endOffset, reference)
                t.xml != null -> Triple(t.xml.textRange.startOffset, t.xml.textRange.endOffset, "\"$reference\"")
                else -> return@mapNotNull null
            }
            Edit(start = s, end = e, replacementText = text, key = row.key, kind = t.kind)
        }.sortedByDescending { it.start }

        val seenKeysInDefault = mutableSetOf<String>()

        WriteCommandAction.runWriteCommandAction(project, "Batch Extract Strings", null, {
            val comment = null
            included.forEach { row ->
                if (row.existingKey != null && row.key == row.existingKey) return@forEach
                if (row.key in seenKeysInDefault) return@forEach
                if (StringsXmlUtil.keyExists(result.targetStringsXml, row.key)) {
                    seenKeysInDefault.add(row.key)
                    return@forEach
                }
                StringsXmlUtil.appendEntry(result.targetStringsXml, row.key, row.value, comment, settings.sortAfterExtract)
                seenKeysInDefault.add(row.key)
                result.localeSelections.filter { it.include }.forEach { loc ->
                    if (!StringsXmlUtil.keyExists(loc.file, row.key)) {
                        StringsXmlUtil.appendEntry(loc.file, row.key, row.value, comment, settings.sortAfterExtract)
                    }
                }
            }

            val doc = editor.document
            edits.forEach { edit ->
                doc.replaceString(edit.start, edit.end, edit.replacementText)
            }
            PsiDocumentManager.getInstance(project).commitDocument(doc)

            if (ktFile != null) {
                var added = false
                when (system) {
                    ResourceSystem.ANDROID -> {
                        if (edits.any { it.kind == ExtractContextKind.COMPOSABLE }) {
                            added = KtImportUtil.ensureImport(ktFile, "androidx.compose.ui.res.stringResource") || added
                        }
                        if (androidRPackage != null && edits.any { it.kind != ExtractContextKind.XML_LAYOUT }) {
                            added = KtImportUtil.ensureImport(ktFile, "$androidRPackage.R") || added
                        }
                    }
                    ResourceSystem.COMPOSE_MULTIPLATFORM -> {
                        if (cmpResPackage != null) {
                            added = KtImportUtil.ensureImport(ktFile, "$cmpResPackage.Res") || added
                            edits.map { it.key }.distinct().forEach { key ->
                                added = KtImportUtil.ensureImport(ktFile, "$cmpResPackage.$key") || added
                            }
                            if (edits.any { it.kind == ExtractContextKind.COMPOSABLE }) {
                                added = KtImportUtil.ensureImport(ktFile, "org.jetbrains.compose.resources.stringResource") || added
                            }
                        }
                    }
                }
                if (added) KtImportUtil.optimizeImports(ktFile)
            }
        })
    }
}
