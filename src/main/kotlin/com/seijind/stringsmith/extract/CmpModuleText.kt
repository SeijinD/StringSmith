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

    fun resolveResPackage(
        gradleText: String?,
        existingResImportPackages: List<String>,
        filePackage: String,
        moduleNamespace: String?
    ): String? {
        gradleText?.let { parsePackageOfResClass(it) }?.let { return it }

        pickResImportByFilePackage(existingResImportPackages, filePackage)?.let { return it }

        // Derive from THIS module/file before falling back to an arbitrary import, so a fresh file in
        // a multi-module CMP project doesn't borrow another module's generated Res package.
        val base = moduleNamespace?.takeIf { it.isNotEmpty() }
            ?: filePackage.takeIf { it.isNotEmpty() }
        if (base != null) return "$base$GENERATED_SUFFIX"

        return existingResImportPackages.firstOrNull()
    }
}
