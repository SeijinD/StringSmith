package com.seijind.stringsmith.extract

import com.intellij.lang.LanguageImportStatements
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object Replacement {

    fun referenceFor(target: ExtractTarget, key: String): String {
        val settings = StringSmithSettings.getInstance()
        val base = when (target.kind) {
            ExtractContextKind.COMPOSABLE -> settings.composeStyle.template.format(key)
            ExtractContextKind.ANDROID_CLASS -> settings.activityStyle.template.format(key)
            ExtractContextKind.KOTLIN_GENERIC -> "R.string.$key"
            ExtractContextKind.XML_LAYOUT -> "@string/$key"
        }
        if (target.formatArgs.isEmpty()) return base
        return when (target.kind) {
            ExtractContextKind.COMPOSABLE,
            ExtractContextKind.ANDROID_CLASS -> insertArgsBeforeClose(base, target.formatArgs)
            else -> base
        }
    }

    private fun insertArgsBeforeClose(base: String, args: List<String>): String {
        val lastClose = base.lastIndexOf(')')
        if (lastClose < 0) return base
        val argList = args.joinToString(", ")
        return base.substring(0, lastClose) + ", " + argList + base.substring(lastClose)
    }

    fun apply(editor: Editor, target: ExtractTarget, key: String) {
        val replacement = referenceFor(target, key)
        val doc = editor.document
        when {
            target.kotlin != null -> {
                val expr = target.kotlin
                val range = expr.textRange
                doc.replaceString(range.startOffset, range.endOffset, replacement)
            }
            target.xml != null -> {
                val v = target.xml
                val range = v.textRange
                doc.replaceString(range.startOffset, range.endOffset, "\"$replacement\"")
            }
        }
        PsiDocumentManager.getInstance(target.containingFile.project).commitDocument(doc)
        addKotlinImports(target)
    }

    private fun addKotlinImports(target: ExtractTarget) {
        val file = target.containingFile as? KtFile ?: return
        var added = false
        if (target.kind == ExtractContextKind.COMPOSABLE) {
            added = ensureImport(file, COMPOSE_IMPORT) || added
        }
        if (target.kind != ExtractContextKind.XML_LAYOUT) {
            val vf = target.containingFile.virtualFile
            val rPkg = vf?.let { AndroidModuleUtil.findRPackage(it, file) }
            if (rPkg != null) added = ensureImport(file, "$rPkg.R") || added
        }
        if (added) optimizeImports(file)
    }

    private fun optimizeImports(file: KtFile) {
        val optimizer = LanguageImportStatements.INSTANCE.forFile(file).firstOrNull() ?: return
        optimizer.processFile(file).run()
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

    private const val COMPOSE_IMPORT = "androidx.compose.ui.res.stringResource"
}
