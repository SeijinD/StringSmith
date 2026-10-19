package com.seijind.stringsmith.extract

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Shared Kotlin import helpers used by single and batch extract writers. */
object KtImportUtil {

    /**
     * Adds an `import` for [fqName] in sorted position if absent. Returns true if it was added. The import
     * optimizer is deliberately not run: it would also remove the user's own unused imports.
     */
    fun ensureImport(file: KtFile, fqName: String): Boolean {
        val importList = file.importList ?: return false
        val existing = importList.imports
        if (existing.any { it.importedFqName?.asString() == fqName }) return false
        val factory = KtPsiFactory(file.project)
        val newImport = factory.createFile("import $fqName").importDirectives.firstOrNull() ?: return false
        val next = existing.firstOrNull { (it.importedFqName?.asString() ?: "") > fqName }
        when {
            next != null -> {
                importList.addBefore(newImport, next)
                importList.addBefore(factory.createNewLine(), next)
            }
            existing.isNotEmpty() -> {
                val added = importList.addAfter(newImport, existing.last())
                importList.addBefore(factory.createNewLine(), added)
            }
            else -> importList.add(newImport)
        }
        return true
    }

    /** Resolves the generated `Res` package for [ktFile]; call it outside the write command (see [Replacement.importPackagesFor]). */
    fun cmpResPackage(ktFile: KtFile): String? =
        ktFile.virtualFile?.let { CmpModuleUtil.findResPackage(ktFile.project, it, ktFile) }

    /** Imports the Compose Multiplatform `Res.string.<key>` accessor from [resPackage]. */
    fun addCmpKeyImport(ktFile: KtFile, resPackage: String, key: String) {
        ensureImport(ktFile, "$resPackage.$key")
    }

    const val ANDROID_COMPOSE_IMPORT = "androidx.compose.ui.res.stringResource"
    const val CMP_COMPOSE_IMPORT = "org.jetbrains.compose.resources.stringResource"

    /**
     * The fully-qualified imports an extracted reference needs — single source of truth shared by the
     * single and batch writers so they can't drift. [keys] are the CMP `Res.string.<key>` imports (one
     * per distinct key). Pure: no PSI, so it is unit-testable on its own.
     */
    fun resourceImportsFor(
        system: ResourceSystem,
        hasComposable: Boolean,
        hasNonXmlReference: Boolean,
        keys: Collection<String>,
        androidRPackage: String?,
        cmpResPackage: String?
    ): List<String> = buildList {
        when (system) {
            ResourceSystem.ANDROID -> {
                if (hasComposable) add(ANDROID_COMPOSE_IMPORT)
                if (androidRPackage != null && hasNonXmlReference) add("$androidRPackage.R")
            }
            ResourceSystem.COMPOSE_MULTIPLATFORM -> {
                if (cmpResPackage != null) {
                    add("$cmpResPackage.Res")
                    keys.distinct().forEach { add("$cmpResPackage.$it") }
                    if (hasComposable) add(CMP_COMPOSE_IMPORT)
                }
            }
        }
    }

    /** Ensures every import from [resourceImportsFor] is present. */
    fun addResourceImports(
        file: KtFile,
        system: ResourceSystem,
        hasComposable: Boolean,
        hasNonXmlReference: Boolean,
        keys: Collection<String>,
        androidRPackage: String?,
        cmpResPackage: String?
    ) {
        resourceImportsFor(system, hasComposable, hasNonXmlReference, keys, androidRPackage, cmpResPackage)
            .forEach { ensureImport(file, it) }
    }
}
