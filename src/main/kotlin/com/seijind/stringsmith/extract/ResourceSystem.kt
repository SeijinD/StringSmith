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
        fun of(stringsXml: VirtualFile): ResourceSystem = of(stringsXml.path)

        fun of(path: String): ResourceSystem {
            val normalized = path.replace('\\', '/')
            return if (normalized.contains("/composeResources/")) COMPOSE_MULTIPLATFORM else ANDROID
        }
    }
}
