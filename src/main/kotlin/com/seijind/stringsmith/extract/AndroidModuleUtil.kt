package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile

object AndroidModuleUtil {

    fun findRPackage(file: VirtualFile, ktFile: KtFile? = null): String? {
        val moduleRoot = findModuleRoot(file) ?: return null
        val filePackage = ktFile?.packageFqName?.asString().orEmpty()
        return AndroidModuleText.resolveRPackage(
            gradleText = readGradleText(moduleRoot),
            manifestText = readManifestText(moduleRoot),
            filePath = file.path,
            moduleRootPath = moduleRoot.path,
            filePackage = filePackage
        )
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

    private fun readGradleText(moduleRoot: VirtualFile): String? {
        val gradle = moduleRoot.findChild("build.gradle.kts") ?: moduleRoot.findChild("build.gradle") ?: return null
        return FileDocumentManager.getInstance().getDocument(gradle)?.text
            ?: String(gradle.contentsToByteArray(), Charsets.UTF_8)
    }

    private fun readManifestText(moduleRoot: VirtualFile): String? {
        val manifest = moduleRoot.findFileByRelativePath("src/main/AndroidManifest.xml") ?: return null
        return FileDocumentManager.getInstance().getDocument(manifest)?.text
            ?: String(manifest.contentsToByteArray(), Charsets.UTF_8)
    }
}
