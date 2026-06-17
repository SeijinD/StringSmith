package com.seijind.stringsmith.extract

import com.seijind.stringsmith.settings.NamingConvention
import java.text.Normalizer

object KeyGenerator {

    private val KEY_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*")

    fun isValidKey(key: String): Boolean = key.matches(KEY_PATTERN)

    fun suggest(value: String, prefix: String, naming: NamingConvention, maxLength: Int): String {
        val ascii = transliterate(value).replace(Regex("[^A-Za-z0-9]+"), " ").trim()
        // Non-Latin text (Greek, Cyrillic, CJK, emoji-only, …) strips to empty. Fall back to a stable
        // per-value token so distinct strings get distinct keys instead of all collapsing to "label".
        val cleaned = ascii.ifEmpty { fallbackToken(value) }
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
        val valid = ensureLetterStart(withPrefix, naming)
        return valid.take(maxLength.coerceAtLeast(1)).trimEnd('_').ifEmpty { "label" }
    }

    // Android resource names must start with a letter; prepend "key" for digit-leading values.
    private fun ensureLetterStart(candidate: String, naming: NamingConvention): String {
        if (candidate.isEmpty() || candidate.first().isLetter()) return candidate
        return when (naming) {
            NamingConvention.SNAKE_CASE -> "key_$candidate"
            NamingConvention.CAMEL_CASE -> "key" + candidate.replaceFirstChar { it.uppercaseChar() }
        }
    }

    // Romanize source text to ASCII: drop diacritics from accented Latin (café -> cafe), then map
    // Greek and Cyrillic letters to Latin (Καλημέρα -> kalimera). Scripts without a mapping (CJK,
    // Arabic, emoji) pass through unchanged and are handled by the empty-result fallback.
    private fun transliterate(value: String): String {
        val noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val sb = StringBuilder(noMarks.length)
        for (ch in noMarks) {
            val mapped = ROMANIZE[ch.lowercaseChar()]
            when {
                mapped == null -> sb.append(ch)
                ch.isUpperCase() -> sb.append(mapped.replaceFirstChar { it.uppercaseChar() })
                else -> sb.append(mapped)
            }
        }
        return sb.toString()
    }

    // Deterministic for a given text (String.hashCode is specified), so the same string always
    // suggests the same key, and different strings get different keys. Last resort for scripts with
    // no romanization above (e.g. CJK), where transliteration would otherwise yield an empty key.
    private fun fallbackToken(value: String): String =
        "label " + value.trim().hashCode().toUInt().toString(16)

    private val ROMANIZE: Map<Char, String> = buildMap {
        // Greek (monotonic; accents already removed by NFD)
        putAll(
            mapOf(
                'α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e", 'ζ' to "z",
                'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k", 'λ' to "l", 'μ' to "m",
                'ν' to "n", 'ξ' to "x", 'ο' to "o", 'π' to "p", 'ρ' to "r", 'σ' to "s",
                'ς' to "s", 'τ' to "t", 'υ' to "y", 'φ' to "f", 'χ' to "ch", 'ψ' to "ps", 'ω' to "o"
            )
        )
        // Cyrillic
        putAll(
            mapOf(
                'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
                'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
                'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
                'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
                'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
            )
        )
    }

    private fun toCamel(spaced: String): String {
        val parts = spaced.split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        return parts.first().lowercase() +
            parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
