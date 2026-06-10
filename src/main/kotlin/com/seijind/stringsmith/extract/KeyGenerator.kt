package com.seijind.stringsmith.extract

import com.seijind.stringsmith.settings.NamingConvention

object KeyGenerator {

    private val KEY_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*")

    fun isValidKey(key: String): Boolean = key.matches(KEY_PATTERN)

    fun suggest(value: String, prefix: String, naming: NamingConvention, maxLength: Int): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9]+"), " ").trim()
        val base = when (naming) {
            NamingConvention.SNAKE_CASE -> cleaned.lowercase().replace(' ', '_')
            NamingConvention.CAMEL_CASE -> toCamel(cleaned)
        }
        val withPrefix = if (prefix.isNotBlank()) {
            val cleanPrefix = prefix.trim().trimEnd('_')
            when (naming) {
                NamingConvention.SNAKE_CASE -> "${cleanPrefix.lowercase()}_$base"
                NamingConvention.CAMEL_CASE -> cleanPrefix + base.replaceFirstChar { it.uppercaseChar() }
            }
        } else base
        return withPrefix.take(maxLength.coerceAtLeast(1)).ifEmpty { "label" }
    }

    private fun toCamel(spaced: String): String {
        val parts = spaced.split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        return parts.first().lowercase() +
            parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
