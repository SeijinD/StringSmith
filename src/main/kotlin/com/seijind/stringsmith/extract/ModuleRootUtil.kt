package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/** Shared module-root discovery used by both the Android `R` and the CMP `Res` package resolvers. */
object ModuleRootUtil {

    fun findModuleRoot(file: VirtualFile): VirtualFile? {
        var current: VirtualFile? = file.parent
        while (current != null) {
            val hasGradle = current.findChild("build.gradle.kts") != null || current.findChild("build.gradle") != null
            val hasManifest = current.findFileByRelativePath("src/main/AndroidManifest.xml") != null
            if (hasGradle || hasManifest) return current
            current = current.parent
        }
        return null
    }

    fun readGradleText(moduleRoot: VirtualFile): String? {
        val gradle = moduleRoot.findChild("build.gradle.kts") ?: moduleRoot.findChild("build.gradle") ?: return null
        return FileDocumentManager.getInstance().getDocument(gradle)?.text
            ?: String(gradle.contentsToByteArray(), Charsets.UTF_8)
    }
}
