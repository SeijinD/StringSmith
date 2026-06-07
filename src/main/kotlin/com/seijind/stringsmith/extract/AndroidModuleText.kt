package com.seijind.stringsmith.extract

object AndroidModuleText {

    private val NAMESPACE_REGEX = Regex("""namespace\s*=?\s*["']([\w.]+)["']""")
    private val MANIFEST_PACKAGE_REGEX = Regex("""package\s*=\s*"([\w.]+)"""")

    fun parseNamespaces(gradleText: String): List<String> =
        NAMESPACE_REGEX.findAll(gradleText)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .distinct()
            .toList()

    fun parseManifestPackage(manifestText: String): String? =
        MANIFEST_PACKAGE_REGEX.find(manifestText)?.groupValues?.getOrNull(1)

    fun pickByFilePackagePrefix(candidates: List<String>, filePackage: String): String? {
        if (filePackage.isEmpty()) return null
        return candidates
            .filter { filePackage == it || filePackage.startsWith("$it.") }
            .maxByOrNull { it.length }
    }

    fun deriveFromSourceLayout(filePath: String, moduleRootPath: String, filePackage: String): String? {
        if (filePackage.isEmpty()) return null
        val normalizedRoot = moduleRootPath.replace('\\', '/').trimEnd('/')
        val normalizedFile = filePath.replace('\\', '/')
        if (!normalizedFile.startsWith("$normalizedRoot/")) return null
        val relative = normalizedFile.removePrefix("$normalizedRoot/")
        for (marker in listOf("/java/", "/kotlin/")) {
            val idx = relative.indexOf(marker)
            if (idx < 0) continue
            val afterSrc = relative.substring(idx + marker.length).substringBeforeLast('/')
            val pkgFromDir = afterSrc.replace('/', '.')
            if (filePackage == pkgFromDir || filePackage.startsWith("$pkgFromDir.")) {
                return pkgFromDir
            }
            val match = longestCommonDottedPrefix(filePackage, pkgFromDir)
            if (match.isNotEmpty()) return match
        }
        return null
    }

    fun longestCommonDottedPrefix(a: String, b: String): String {
        val aParts = a.split('.')
        val bParts = b.split('.')
        val out = mutableListOf<String>()
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            if (aParts[i] != bParts[i]) break
            out += aParts[i]
        }
        return out.joinToString(".")
    }

    fun resolveRPackage(
        gradleText: String?,
        manifestText: String?,
        filePath: String?,
        moduleRootPath: String?,
        filePackage: String
    ): String? {
        val gradleNamespaces = gradleText?.let { parseNamespaces(it) } ?: emptyList()
        pickByFilePackagePrefix(gradleNamespaces, filePackage)?.let { return it }

        val manifestPkg = manifestText?.let { parseManifestPackage(it) }
        pickByFilePackagePrefix(listOfNotNull(manifestPkg), filePackage)?.let { return it }

        if (filePath != null && moduleRootPath != null) {
            deriveFromSourceLayout(filePath, moduleRootPath, filePackage)?.let { return it }
        }

        return gradleNamespaces.firstOrNull() ?: manifestPkg
    }
}
