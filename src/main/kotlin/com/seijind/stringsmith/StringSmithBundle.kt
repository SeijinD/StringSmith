package com.seijind.stringsmith

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.StringSmithBundle"

object StringSmithBundle {

    private val bundle = DynamicBundle(StringSmithBundle::class.java, BUNDLE)

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}
