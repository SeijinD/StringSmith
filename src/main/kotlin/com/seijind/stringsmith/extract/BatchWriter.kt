package com.seijind.stringsmith.extract

import com.intellij.lang.LanguageImportStatements
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

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
            val rPackage: String?,
            val needsComposeImport: Boolean
        )

        val ktFile = included.firstOrNull()?.target?.containingFile as? KtFile
        val rPackage = ktFile?.virtualFile?.let { AndroidModuleUtil.findRPackage(it, ktFile) }

        val edits = included.mapNotNull { row ->
            val t = row.target
            val reference = Replacement.referenceFor(t, row.key)
            val (s, e, text) = when {
                t.kotlin != null -> Triple(t.kotlin.textRange.startOffset, t.kotlin.textRange.endOffset, reference)
                t.xml != null -> Triple(t.xml.textRange.startOffset, t.xml.textRange.endOffset, "\"$reference\"")
                else -> return@mapNotNull null
            }
            Edit(
                start = s,
                end = e,
                replacementText = text,
                rPackage = rPackage.takeIf { t.kind != ExtractContextKind.XML_LAYOUT },
                needsComposeImport = t.kind == ExtractContextKind.COMPOSABLE
            )
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
                if (edits.any { it.needsComposeImport }) {
                    added = ensureImport(ktFile, "androidx.compose.ui.res.stringResource") || added
                }
                edits.mapNotNull { it.rPackage }.distinct().forEach { pkg ->
                    added = ensureImport(ktFile, "$pkg.R") || added
                }
                if (added) optimizeImports(ktFile)
            }
        })
    }

    private fun ensureImport(file: KtFile, fqName: String): Boolean {
        val imports = file.importList ?: return false
        val already = imports.imports.any { it.importedFqName?.asString() == fqName }
        if (already) return false
        val factory = KtPsiFactory(file.project)
        val parsed = factory.createFile("import $fqName")
        val newImport = parsed.importDirectives.firstOrNull() ?: return false
        imports.add(newImport)
        return true
    }

    private fun optimizeImports(file: KtFile) {
        val optimizer = LanguageImportStatements.INSTANCE.forFile(file).firstOrNull() ?: return
        optimizer.processFile(file).run()
    }
}
