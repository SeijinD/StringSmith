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

    /** True when the reference for [system] has no place for the template's arguments, so they would be lost. */
    fun dropsFormatArgs(target: ExtractTarget, system: ResourceSystem): Boolean =
        target.formatArgs.isNotEmpty() && !injectsArgs(system, target.kind)

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

    /** Packages for the reference's imports. */
    class ImportPackages(val androidRPackage: String?, val cmpResPackage: String?)

    /**
     * Resolve before the write command: the CMP lookup scans project imports, and inside the command its
     * PSI-keyed cache is always stale (the edit just changed PSI), so it would rescan under the write lock.
     */
    fun importPackagesFor(target: ExtractTarget, system: ResourceSystem): ImportPackages {
        val file = target.containingFile as? KtFile ?: return ImportPackages(null, null)
        val vf = file.virtualFile ?: return ImportPackages(null, null)
        return when (system) {
            ResourceSystem.ANDROID -> ImportPackages(AndroidModuleUtil.findRPackage(vf, file), null)
            ResourceSystem.COMPOSE_MULTIPLATFORM -> ImportPackages(null, CmpModuleUtil.findResPackage(file.project, vf, file))
        }
    }

    fun apply(editor: Editor, target: ExtractTarget, key: String, system: ResourceSystem, packages: ImportPackages) {
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
        addKotlinImports(target, key, system, packages)
    }

    private fun addKotlinImports(target: ExtractTarget, key: String, system: ResourceSystem, packages: ImportPackages) {
        val file = target.containingFile as? KtFile ?: return
        KtImportUtil.addResourceImports(
            file = file,
            system = system,
            hasComposable = target.kind == ExtractContextKind.COMPOSABLE,
            hasNonXmlReference = target.kind != ExtractContextKind.XML_LAYOUT,
            keys = listOf(key),
            androidRPackage = packages.androidRPackage,
            cmpResPackage = packages.cmpResPackage
        )
    }
}
