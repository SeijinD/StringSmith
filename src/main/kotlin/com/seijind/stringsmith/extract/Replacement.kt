package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

object Replacement {

    fun referenceFor(target: ExtractTarget, key: String): String = when (target.kind) {
        ExtractContextKind.COMPOSABLE -> "stringResource(R.string.$key)"
        ExtractContextKind.ANDROID_CLASS -> "getString(R.string.$key)"
        ExtractContextKind.KOTLIN_GENERIC -> "R.string.$key"
        ExtractContextKind.XML_LAYOUT -> "@string/$key"
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
        val newImport = factory.createImportDirective(ImportPath.fromString(fqName))
        imports.add(newImport)
    }

    private const val COMPOSE_IMPORT = "androidx.compose.ui.res.stringResource"
}
