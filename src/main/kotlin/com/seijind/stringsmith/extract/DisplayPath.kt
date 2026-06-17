package com.seijind.stringsmith.extract

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object DisplayPath {

    /** [file]'s path made relative to the project root (forward slashes), or the full path if outside it. */
    fun projectRelative(project: Project, file: VirtualFile): String {
        val base = project.basePath?.replace('\\', '/')?.trimEnd('/')
        val normalized = file.path.replace('\\', '/')
        return base
            ?.takeIf { normalized.startsWith("$it/") }
            ?.let { normalized.removePrefix("$it/") }
            ?: normalized
    }
}
