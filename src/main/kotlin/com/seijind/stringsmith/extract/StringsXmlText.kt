package com.seijind.stringsmith.extract

object StringsXmlText {

    // `\s+` after <string avoids matching <string-array>/<plurals>. Groups: 1=quote, 2=key, 3=value.
    private val ENTRY_REGEX = Regex("""<string\s+[^>]*?\bname\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)</string>""")

    // A `<string>` inside a comment is dead: never parse or sort it as a live entry.
    private val COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")

    private fun liveEntryMatches(text: String): List<MatchResult> {
        val commentRanges = COMMENT_REGEX.findAll(text).map { it.range }.toList()
        return ENTRY_REGEX.findAll(text)
            .filter { m -> commentRanges.none { m.range.first in it } }
            .toList()
    }

    fun parseEntries(text: String): List<StringsXmlEntry> =
        liveEntryMatches(text).map { m ->
            StringsXmlEntry(m.groupValues[2], decodeXml(m.groupValues[3]))
        }

    fun appendEntry(text: String, key: String, value: String, comment: String? = null, sortAlpha: Boolean = false): String =
        appendEntries(text, listOf(key to value), comment, sortAlpha)

    /** Inserts all [entries] before `</resources>` in one pass, then sorts once if requested. */
    fun appendEntries(
        text: String,
        entries: List<Pair<String, String>>,
        comment: String? = null,
        sortAlpha: Boolean = false
    ): String {
        if (entries.isEmpty()) return text
        val commentLine = if (!comment.isNullOrBlank()) "    <!-- $comment -->\n" else ""
        val block = buildString {
            for ((key, value) in entries) {
                append(commentLine)
                append("    <string name=\"").append(key).append("\">")
                append(encodeXml(value))
                append("</string>\n")
            }
        }
        val closeIdx = text.lastIndexOf("</resources>")
        var newText = if (closeIdx >= 0) {
            text.substring(0, closeIdx) + block + text.substring(closeIdx)
        } else {
            buildString {
                append(text.trimEnd())
                append("\n<resources>\n")
                append(block)
                append("</resources>\n")
            }
        }
        if (sortAlpha) newText = sortStringEntries(newText)
        return newText
    }

    /**
     * Sorts live `<string>` entries alphabetically in place: each block moves into a slot the blocks
     * already occupy; everything else (comments, commented-out entries, plurals, arrays, whitespace)
     * stays byte-for-byte untouched.
     */
    fun sortStringEntries(xml: String): String {
        val matches = liveEntryMatches(xml)
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
        // Fast path: no escape/entity markers means nothing to unescape — skip the chained replaces.
        if (value.indexOf('\\') < 0 && value.indexOf('&') < 0) return value.trim()
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
