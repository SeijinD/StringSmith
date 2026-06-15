package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile

object Replacement {

    fun referenceFor(target: ExtractTarget, key: String, system: ResourceSystem): String {
        val settings = StringSmithSettings.getInstance()
        val base = when (system) {
            ResourceSystem.ANDROID -> when (target.kind) {
                ExtractContextKind.COMPOSABLE -> settings.composeStyle.template.format(key)
                ExtractContextKind.ANDROID_CLASS -> settings.activityStyle.template.format(key)
                ExtractContextKind.KOTLIN_GENERIC -> "R.string.$key"
                ExtractContextKind.XML_LAYOUT -> "@string/$key"
            }
            ResourceSystem.COMPOSE_MULTIPLATFORM -> when (target.kind) {
                ExtractContextKind.COMPOSABLE -> "stringResource(Res.string.$key)"
                ExtractContextKind.XML_LAYOUT -> "@string/$key"
                else -> "Res.string.$key"
            }
        }
        if (target.formatArgs.isEmpty()) return base
        return if (injectsArgs(system, target.kind)) insertArgsBeforeClose(base, target.formatArgs) else base
    }

    private fun injectsArgs(system: ResourceSystem, kind: ExtractContextKind): Boolean = when (system) {
        ResourceSystem.ANDROID ->
            kind == ExtractContextKind.COMPOSABLE || kind == ExtractContextKind.ANDROID_CLASS
        ResourceSystem.COMPOSE_MULTIPLATFORM ->
            kind == ExtractContextKind.COMPOSABLE
    }

    private fun insertArgsBeforeClose(base: String, args: List<String>): String {
        val lastClose = base.lastIndexOf(')')
        if (lastClose < 0) return base
        val argList = args.joinToString(", ")
        return base.substring(0, lastClose) + ", " + argList + base.substring(lastClose)
    }

    fun apply(editor: Editor, target: ExtractTarget, key: String, system: ResourceSystem) {
        val replacement = referenceFor(target, key, system)
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
        addKotlinImports(target, key, system)
    }

    private fun addKotlinImports(target: ExtractTarget, key: String, system: ResourceSystem) {
        val file = target.containingFile as? KtFile ?: return
        var added = false
        when (system) {
            ResourceSystem.ANDROID -> {
                if (target.kind == ExtractContextKind.COMPOSABLE) {
                    added = KtImportUtil.ensureImport(file, ANDROID_COMPOSE_IMPORT) || added
                }
                if (target.kind != ExtractContextKind.XML_LAYOUT) {
                    val vf = target.containingFile.virtualFile
                    val rPkg = vf?.let { AndroidModuleUtil.findRPackage(it, file) }
                    if (rPkg != null) added = KtImportUtil.ensureImport(file, "$rPkg.R") || added
                }
            }
            ResourceSystem.COMPOSE_MULTIPLATFORM -> {
                val vf = target.containingFile.virtualFile
                val resPkg = vf?.let { CmpModuleUtil.findResPackage(file.project, it, file) }
                if (resPkg != null) {
                    added = KtImportUtil.ensureImport(file, "$resPkg.Res") || added
                    added = KtImportUtil.ensureImport(file, "$resPkg.$key") || added
                    if (target.kind == ExtractContextKind.COMPOSABLE) {
                        added = KtImportUtil.ensureImport(file, CMP_COMPOSE_IMPORT) || added
                    }
                }
            }
        }
        if (added) KtImportUtil.optimizeImports(file)
    }

    private const val ANDROID_COMPOSE_IMPORT = "androidx.compose.ui.res.stringResource"
    private const val CMP_COMPOSE_IMPORT = "org.jetbrains.compose.resources.stringResource"
}
