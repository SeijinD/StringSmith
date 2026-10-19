package com.seijind.stringsmith.extract

/**
 * Pure, testable resolver for the Compose Multiplatform generated `Res` class package
 * (`<pkg>.generated.resources`). Mirrors [AndroidModuleText] for the Android `R` package.
 */
object CmpModuleText {

    private const val GENERATED_SUFFIX = ".generated.resources"

    private val PACKAGE_OF_RES_CLASS =
        Regex("""packageOfResClass\s*(?:=|\.set\s*\()\s*["']([\w.]+)["']""")

    fun parsePackageOfResClass(gradleText: String): String? =
        PACKAGE_OF_RES_CLASS.find(gradleText)?.groupValues?.getOrNull(1)

    /** Picks the `*.generated.resources` import whose base package best prefixes the file package. */
    fun pickResImportByFilePackage(candidates: List<String>, filePackage: String): String? {
        if (filePackage.isEmpty()) return null
        return candidates
            .filter {
                val base = it.removeSuffix(GENERATED_SUFFIX)
                filePackage == base || filePackage.startsWith("$base.")
            }
            .maxByOrNull { it.length }
    }

    /**
     * Whether [resPackage] is the Res package CMP generates for [moduleName] by default
     * (`<group>.<module>.generated.resources`, module name lowercased with separators dropped).
     */
    fun isModuleResPackage(resPackage: String, moduleName: String): Boolean {
        if (!resPackage.endsWith(GENERATED_SUFFIX)) return false
        val segment = resPackage.removeSuffix(GENERATED_SUFFIX).substringAfterLast('.')
        return normalized(segment) == normalized(moduleName)
    }

    private fun normalized(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    fun resolveResPackage(
        gradleText: String?,
        moduleResImportPackages: List<String>,
        existingResImportPackages: List<String>,
        filePackage: String,
        moduleNamespace: String?
    ): String? {
        gradleText?.let { parsePackageOfResClass(it) }?.let { return it }

        // The module's own Res, already imported by its code; its package need not match the code package
        // (the CMP wizard puts Res in `<project>.<module>.generated.resources`).
        if (moduleResImportPackages.isNotEmpty()) {
            return pickResImportByFilePackage(moduleResImportPackages, filePackage) ?: moduleResImportPackages.first()
        }

        pickResImportByFilePackage(existingResImportPackages, filePackage)?.let { return it }

        // Derive from THIS module/file before falling back to an arbitrary import, so a fresh file in
        // a multi-module CMP project doesn't borrow another module's generated Res package.
        val base = moduleNamespace?.takeIf { it.isNotEmpty() }
            ?: filePackage.takeIf { it.isNotEmpty() }
        if (base != null) return "$base$GENERATED_SUFFIX"

        return existingResImportPackages.firstOrNull()
    }
}
