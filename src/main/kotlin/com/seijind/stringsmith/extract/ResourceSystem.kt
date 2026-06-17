package com.seijind.stringsmith.extract

import com.intellij.openapi.vfs.VirtualFile

/**
 * The resource system a target `strings.xml` belongs to. Android keeps strings under `res/values/`
 * and references them via `R.string` / androidx `stringResource`; Compose Multiplatform keeps them
 * under `composeResources/values/` and references them via a generated `Res` class.
 */
enum class ResourceSystem {
    ANDROID,
    COMPOSE_MULTIPLATFORM;

    companion object {
        /** Directory that marks a Compose Multiplatform resource tree (`…/composeResources/values/…`). */
        const val CMP_PATH_MARKER = "composeResources"

        /** Reference forms a string key can appear in across the resource systems. */
        const val ANDROID_REF_PREFIX = "R.string."
        const val CMP_REF_PREFIX = "Res.string."
        const val XML_REF_PREFIX = "@string/"

        fun of(stringsXml: VirtualFile): ResourceSystem = of(stringsXml.path)

        fun of(path: String): ResourceSystem {
            // Check both separators directly instead of allocating a normalized copy of the path.
            return if (path.contains("/$CMP_PATH_MARKER/") || path.contains("\\$CMP_PATH_MARKER\\"))
                COMPOSE_MULTIPLATFORM else ANDROID
        }
    }
}
