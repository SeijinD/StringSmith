package com.seijind.stringsmith.extract

import com.intellij.lang.LanguageImportStatements
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Shared Kotlin import helpers used by single and batch extract writers. */
object KtImportUtil {

    /** Adds an `import` for [fqName] if absent. Returns true if it was added. */
    fun ensureImport(file: KtFile, fqName: String): Boolean {
        val imports = file.importList ?: return false
        val already = imports.imports.any { it.importedFqName?.asString() == fqName }
        if (already) return false
        val factory = KtPsiFactory(file.project)
        val parsed = factory.createFile("import $fqName")
        val newImport = parsed.importDirectives.firstOrNull() ?: return false
        imports.add(newImport)
        return true
    }

    fun optimizeImports(file: KtFile) {
        val optimizer = LanguageImportStatements.INSTANCE.forFile(file).firstOrNull() ?: return
        optimizer.processFile(file).run()
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

    /** Ensures every import from [resourceImportsFor] is present, then optimizes if anything was added. */
    fun addResourceImports(
        file: KtFile,
        system: ResourceSystem,
        hasComposable: Boolean,
        hasNonXmlReference: Boolean,
        keys: Collection<String>,
        androidRPackage: String?,
        cmpResPackage: String?
    ) {
        var added = false
        resourceImportsFor(system, hasComposable, hasNonXmlReference, keys, androidRPackage, cmpResPackage)
            .forEach { added = ensureImport(file, it) || added }
        if (added) optimizeImports(file)
    }
}
