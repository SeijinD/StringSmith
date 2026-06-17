package com.seijind.stringsmith.extract

object StringsXmlText {

    // Matches a <string> element with a `name` attribute in ANY position, single- or double-quoted.
    // `<string\s+` (whitespace required) keeps this from matching <string-array> / <string-plurals>.
    // Groups: 1 = quote char, 2 = key, 3 = inner value.
    private val ENTRY_REGEX = Regex("""<string\s+[^>]*?\bname\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)</string>""")
    private val ENTRY_WITH_COMMENT_REGEX = Regex("""(?:\s*<!--[^>]*-->)?\s*<string\s+name\s*=\s*"([^"]+)"[\s\S]*?</string>""")

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

    fun sortStringEntries(xml: String): String {
        val openIdx = xml.indexOf("<resources")
        val openEnd = if (openIdx >= 0) xml.indexOf('>', openIdx) + 1 else return xml
        val closeIdx = xml.lastIndexOf("</resources>")
        if (openEnd <= 0 || closeIdx <= openEnd) return xml
        val inner = xml.substring(openEnd, closeIdx)
        val matches = ENTRY_WITH_COMMENT_REGEX.findAll(inner).toList()
        if (matches.size < 2) return xml
        val sorted = matches.sortedBy { it.groupValues[1] }.joinToString("\n") { it.value.trim() }
        return xml.substring(0, openEnd) + "\n    " + sorted.replace("\n", "\n    ") + "\n" + xml.substring(closeIdx)
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
