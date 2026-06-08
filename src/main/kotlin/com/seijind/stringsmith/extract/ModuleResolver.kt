package com.seijind.stringsmith.extract

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project
import com.seijind.stringsmith.settings.StringSmithSettings

object ModuleResolver {

    fun chooseInitialTarget(
        project: Project,
        file: PsiFile,
        allTargets: List<VirtualFile>,
        settings: StringSmithSettings
    ): VirtualFile {
        val nearest = StringsXmlUtil.findDefaultStringsXml(project, file.virtualFile)
        if (nearest != null && isFileInsideStringsXmlModule(file, nearest)) return nearest
        val remembered = settings.lastTargetModulePath
            .takeIf { it.isNotBlank() }
            ?.let { rem -> allTargets.firstOrNull { it.path == rem } }
        return remembered ?: nearest ?: allTargets.first()
    }

    fun isFileInsideStringsXmlModule(file: PsiFile, stringsXml: VirtualFile): Boolean {
        val moduleRoot = stringsXml.parent?.parent?.parent?.parent?.parent ?: return false
        val filePath = file.virtualFile?.path?.replace('\\', '/') ?: return false
        val modulePath = moduleRoot.path.replace('\\', '/').trimEnd('/')
        return filePath.startsWith("$modulePath/")
    }
}
