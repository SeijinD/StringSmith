package com.seijind.stringsmith.extract

object StringsXmlText {

    // Matches a <string> element with a `name` attribute in ANY position, single- or double-quoted.
    // `<string\s+` (whitespace required) keeps this from matching <string-array> / <string-plurals>.
    // Groups: 1 = quote char, 2 = key, 3 = inner value.
    private val ENTRY_REGEX = Regex("""<string\s+[^>]*?\bname\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)</string>""")

    fun parseEntries(text: String): List<StringsXmlEntry> =
        ENTRY_REGEX.findAll(text).map { m ->
            StringsXmlEntry(m.groupValues[2], decodeXml(m.groupValues[3]))
        }.toList()

    fun appendEntry(text: String, key: String, value: String, comment: String? = null, sortAlpha: Boolean = false): String {
        val escaped = encodeXml(value)
        val commentLine = if (!comment.isNullOrBlank()) "    <!-- $comment -->\n" else ""
        val entry = "$commentLine    <string name=\"$key\">$escaped</string>\n"
        val closeIdx = text.lastIndexOf("</resources>")
        var newText = if (closeIdx >= 0) {
            text.substring(0, closeIdx) + entry + text.substring(closeIdx)
        } else {
            buildString {
                append(text.trimEnd())
                append("\n<resources>\n")
                append(entry)
                append("</resources>\n")
            }
        }
        if (sortAlpha) newText = sortStringEntries(newText)
        return newText
    }

    /**
     * Reorders the `<string>` entries alphabetically by name, in place: each `<string>…</string>`
     * block is sorted into the slots the blocks already occupy, while everything between and around
     * them — comments, `<plurals>`, `<string-array>`, whitespace, and each block's verbatim inner
     * text — is left byte-for-byte untouched. Nothing is dropped, re-indented, or reattached.
     */
    fun sortStringEntries(xml: String): String {
        val matches = ENTRY_REGEX.findAll(xml).toList()
        if (matches.size < 2) return xml
        val sortedBlocks = matches.sortedBy { it.groupValues[2] }.map { it.value }
        val sb = StringBuilder(xml.length)
        var last = 0
        matches.forEachIndexed { i, m ->
            sb.append(xml, last, m.range.first)
            sb.append(sortedBlocks[i])
            last = m.range.last + 1
        }
        sb.append(xml, last, xml.length)
        return sb.toString()
    }

    fun encodeXml(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for ((i, ch) in value.withIndex()) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '@' -> if (i == 0) sb.append("\\@") else sb.append(ch)
                '?' -> if (i == 0) sb.append("\\?") else sb.append(ch)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun decodeXml(value: String): String {
        val unescapedLeading = when {
            value.startsWith("\\@") -> "@" + value.substring(2)
            value.startsWith("\\?") -> "?" + value.substring(2)
            else -> value
        }
        return unescapedLeading
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .trim()
    }
}
