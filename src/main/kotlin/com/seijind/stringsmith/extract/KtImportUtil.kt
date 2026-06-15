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
}
