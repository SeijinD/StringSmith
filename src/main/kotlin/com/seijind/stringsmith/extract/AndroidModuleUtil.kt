package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile

object AndroidModuleUtil {

    fun findRPackage(file: VirtualFile, ktFile: KtFile? = null): String? {
        val moduleRoot = ModuleRootUtil.findModuleRoot(file) ?: return null
        val filePackage = ktFile?.packageFqName?.asString().orEmpty()
        return AndroidModuleText.resolveRPackage(
            gradleText = ModuleRootUtil.readGradleText(moduleRoot),
            manifestText = readManifestText(moduleRoot),
            filePath = file.path,
            moduleRootPath = moduleRoot.path,
            filePackage = filePackage
        )
    }

    private fun readManifestText(moduleRoot: VirtualFile): String? {
        val manifest = moduleRoot.findFileByRelativePath("src/main/AndroidManifest.xml") ?: return null
        return FileDocumentManager.getInstance().getDocument(manifest)?.text
            ?: String(manifest.contentsToByteArray(), Charsets.UTF_8)
    }
}
