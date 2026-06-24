package com.seijind.stringsmith.inspections

/** A single format specifier resolved to its argument [position] (1-based) and [conversion] letter. */
data class FormatSpecifier(val position: Int, val conversion: Char)

/** How a translation's format specifiers diverge from the default value's. */
sealed interface FormatMismatch {
    /** The translation references a different number of distinct arguments than the default. */
    data class Count(val defaultCount: Int, val actualCount: Int) : FormatMismatch

    /** Argument [position] has a different conversion type than the default (e.g. `%s` vs `%d`). */
    data class Type(val position: Int, val default: Char, val actual: Char) : FormatMismatch
}

/**
 * Parses Java/Android format strings (`%s`, `%d`, `%1$s`, …) and compares a translation against the
 * default value, mirroring the cross-locale checks Android Lint runs for `R.string` — but also covering
 * Compose Multiplatform `Res.string`, which Lint never sees.
 *
 * Pragmatic, not a full `java.util.Formatter`: `%%` is an escaped literal percent, `%n` a no-argument
 * line separator, and the space / `<` flags are not recognised — so ordinary text such as `"50% off"`
 * is never misread as a specifier.
 */
object FormatSpec {

    private val SPECIFIER = Regex("%%|%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])")

    fun specifiers(text: String): List<FormatSpecifier> {
        val out = mutableListOf<FormatSpecifier>()
        var implicit = 0
        for (match in SPECIFIER.findAll(text)) {
            if (match.value == "%%") continue
            val conversion = match.groupValues[2].single()
            if (conversion == 'n') continue // line separator: consumes no argument
            val explicit = match.groupValues[1]
            val position = if (explicit.isNotEmpty()) explicit.toInt() else ++implicit
            out += FormatSpecifier(position, conversion.lowercaseChar())
        }
        return out
    }

    /** First mismatch between [default] and [translation], or null when their format arguments agree. */
    fun analyze(default: String, translation: String): FormatMismatch? {
        val defaultByPos = byPosition(specifiers(default))
        val actualByPos = byPosition(specifiers(translation))
        if (defaultByPos.keys != actualByPos.keys) {
            return FormatMismatch.Count(defaultByPos.size, actualByPos.size)
        }
        for ((position, conversion) in defaultByPos) {
            val actual = actualByPos.getValue(position)
            if (actual != conversion) return FormatMismatch.Type(position, conversion, actual)
        }
        return null
    }

    // First conversion seen per position; a repeated position (`%1$s … %1$s`) is one argument.
    private fun byPosition(specs: List<FormatSpecifier>): Map<Int, Char> {
        val map = LinkedHashMap<Int, Char>()
        for (s in specs) map.putIfAbsent(s.position, s.conversion)
        return map
    }
}
