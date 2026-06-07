package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object Replacement {

    fun referenceFor(target: ExtractTarget, key: String): String {
        val settings = StringSmithSettings.getInstance()
        return when (target.kind) {
            ExtractContextKind.COMPOSABLE -> settings.composeStyle.template.format(key)
            ExtractContextKind.ANDROID_CLASS -> settings.activityStyle.template.format(key)
            ExtractContextKind.KOTLIN_GENERIC -> "R.string.$key"
            ExtractContextKind.XML_LAYOUT -> "@string/$key"
        }
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
        if (target.kind == ExtractContextKind.COMPOSABLE) {
            ensureImport(file, COMPOSE_IMPORT)
        }
        if (target.kind != ExtractContextKind.XML_LAYOUT) {
            val vf = target.containingFile.virtualFile
            val rPkg = vf?.let { AndroidModuleUtil.findRPackage(it, file) }
            if (rPkg != null) ensureImport(file, "$rPkg.R")
        }
    }

    private fun ensureImport(file: KtFile, fqName: String) {
        val imports = file.importList ?: return
        val already = imports.imports.any { it.importedFqName?.asString() == fqName }
        if (already) return
        val factory = KtPsiFactory(file.project)
        val parsed = factory.createFile("import $fqName")
        val newImport = parsed.importDirectives.firstOrNull() ?: return
        imports.add(newImport)
    }

    private const val COMPOSE_IMPORT = "androidx.compose.ui.res.stringResource"
}
