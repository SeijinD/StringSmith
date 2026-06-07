package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile

object AndroidModuleUtil {

    fun findRPackage(file: VirtualFile, ktFile: KtFile? = null): String? {
        val moduleRoot = findModuleRoot(file) ?: return null
        val filePackage = ktFile?.packageFqName?.asString().orEmpty()

        val gradleCandidates = readAllNamespacesFromGradle(moduleRoot)
        pickByFilePackagePrefix(gradleCandidates, filePackage)?.let { return it }
        pickByFilePackagePrefix(listOfNotNull(readPackageFromManifest(moduleRoot)), filePackage)?.let { return it }
        deriveFromSourceLayout(file, moduleRoot, filePackage)?.let { return it }

        return gradleCandidates.firstOrNull() ?: readPackageFromManifest(moduleRoot)
    }

    private fun pickByFilePackagePrefix(candidates: List<String>, filePackage: String): String? {
        if (filePackage.isEmpty()) return null
        return candidates
            .filter { filePackage == it || filePackage.startsWith("$it.") }
            .maxByOrNull { it.length }
    }

    private fun deriveFromSourceLayout(file: VirtualFile, moduleRoot: VirtualFile, filePackage: String): String? {
        if (filePackage.isEmpty()) return null
        val moduleRootPath = moduleRoot.path.replace('\\', '/').trimEnd('/')
        val filePath = file.path.replace('\\', '/')
        if (!filePath.startsWith("$moduleRootPath/")) return null
        val relative = filePath.removePrefix("$moduleRootPath/")
        val srcMarkers = listOf("/java/", "/kotlin/")
        for (marker in srcMarkers) {
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

    private fun longestCommonDottedPrefix(a: String, b: String): String {
        val aParts = a.split('.')
        val bParts = b.split('.')
        val out = mutableListOf<String>()
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            if (aParts[i] != bParts[i]) break
            out += aParts[i]
        }
        return out.joinToString(".")
    }

    private fun findModuleRoot(file: VirtualFile): VirtualFile? {
        var current: VirtualFile? = file.parent
        while (current != null) {
            val hasGradle = current.findChild("build.gradle.kts") != null || current.findChild("build.gradle") != null
            val hasManifest = current.findFileByRelativePath("src/main/AndroidManifest.xml") != null
            if (hasGradle || hasManifest) return current
            current = current.parent
        }
        return null
    }

    private fun readAllNamespacesFromGradle(moduleRoot: VirtualFile): List<String> {
        val gradle = moduleRoot.findChild("build.gradle.kts") ?: moduleRoot.findChild("build.gradle") ?: return emptyList()
        val text = FileDocumentManager.getInstance().getDocument(gradle)?.text
            ?: String(gradle.contentsToByteArray(), Charsets.UTF_8)
        val regex = Regex("""namespace\s*=?\s*["']([\w.]+)["']""")
        return regex.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.distinct().toList()
    }

    private fun readPackageFromManifest(moduleRoot: VirtualFile): String? {
        val manifest = moduleRoot.findFileByRelativePath("src/main/AndroidManifest.xml") ?: return null
        val text = FileDocumentManager.getInstance().getDocument(manifest)?.text
            ?: String(manifest.contentsToByteArray(), Charsets.UTF_8)
        val regex = Regex("""package\s*=\s*"([\w.]+)"""")
        return regex.find(text)?.groupValues?.getOrNull(1)
    }
}
