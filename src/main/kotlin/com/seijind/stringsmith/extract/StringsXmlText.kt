package com.seijind.stringsmith.extract

object StringsXmlText {

    // `\s+` after <string avoids matching <string-array>/<plurals>. Groups: 1=quote, 2=key, 3=value.
    // The tail `(?:/>|>…</string>)` matches both a normal `<string name="x">v</string>` (group 3 = value)
    // and a self-closed empty `<string name="x"/>` (group 3 absent → treated as an empty value).
    private val ENTRY_REGEX = Regex("""<string\s+[^>]*?\bname\s*=\s*(["'])(.*?)\1[^>]*?(?:/>|>([\s\S]*?)</string>)""")

    // A `<string>` inside a comment is dead: never parse or sort it as a live entry.
    private val COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")

    // Runs of two or more hyphens, illegal inside an XML comment (`--` ends/breaks a comment).
    private val DOUBLE_HYPHEN_REGEX = Regex("-{2,}")

    /**
     * Makes [text] safe to embed in an XML comment: XML 1.0 forbids `--` inside a comment and forbids a
     * comment ending in `-`. Splits any hyphen run with spaces and pads a trailing hyphen.
     */
    private fun sanitizeComment(text: String): String =
        text.replace(DOUBLE_HYPHEN_REGEX) { m -> m.value.toCharArray().joinToString(" ") }
            .let { if (it.endsWith('-')) "$it " else it }

    private fun liveEntryMatches(text: String): List<MatchResult> {
        val commentRanges = COMMENT_REGEX.findAll(text).map { it.range }.toList()
        // An entry is dead if it overlaps a comment region at all — not just where it starts — so an
        // entry straddling a comment boundary is never treated as live.
        return ENTRY_REGEX.findAll(text)
            .filter { m -> commentRanges.none { c -> m.range.first <= c.last && c.first <= m.range.last } }
            .toList()
    }

    fun parseEntries(text: String): List<StringsXmlEntry> =
        liveEntryMatches(text).map { m ->
            StringsXmlEntry(m.groupValues[2], decodeXml(m.groupValues[3]))
        }

    /**
     * Offset of the live `<string name="[key]">` entry's `name` value (the key text), or -1 if absent.
     * Uses [liveEntryMatches] so it never matches a commented-out entry, a `<string-array>`/`<plurals>`,
     * or a longer key — unlike a raw `indexOf`.
     */
    fun offsetOfKey(text: String, key: String): Int =
        liveEntryMatches(text).firstOrNull { it.groupValues[2] == key }?.groups?.get(2)?.range?.first ?: -1

    fun appendEntry(text: String, key: String, value: String, comment: String? = null, sortAlpha: Boolean = false): String =
        appendEntries(text, listOf(StringEntryDraft(key, value, comment)), sortAlpha)

    /** Inserts all [drafts] before `</resources>`, sorting once at the end if asked. */
    fun appendEntries(
        text: String,
        drafts: List<StringEntryDraft>,
        sortAlpha: Boolean = false
    ): String {
        if (drafts.isEmpty()) return text
        val block = buildString {
            for ((key, value, comment) in drafts) {
                if (!comment.isNullOrBlank()) append("    <!-- ").append(sanitizeComment(comment)).append(" -->\n")
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

    /** Replaces the value of the live `<string name="[key]">` entry; no-op if the key is absent. */
    fun updateEntryValue(text: String, key: String, newValue: String): String {
        val m = liveEntryMatches(text).firstOrNull { it.groupValues[2] == key } ?: return text
        val valueGroup = m.groups[3]
        if (valueGroup == null) {
            // Self-closed `<string name="x"/>`: rewrite the whole tag as an open entry carrying the value.
            val openTag = m.value.removeSuffix("/>").trimEnd() + ">"
            return text.substring(0, m.range.first) +
                openTag + encodeXml(newValue) + "</string>" +
                text.substring(m.range.last + 1)
        }
        val valueRange = valueGroup.range
        // For an empty value the range is empty (first > last); first..last+1 still inserts at the right spot.
        return text.substring(0, valueRange.first) + encodeXml(newValue) + text.substring(valueRange.last + 1)
    }

    /** Renames the `name` attribute of the live `<string>` entry [oldKey] to [newKey]; no-op if absent. */
    fun renameEntryKey(text: String, oldKey: String, newKey: String): String {
        val m = liveEntryMatches(text).firstOrNull { it.groupValues[2] == oldKey } ?: return text
        val keyRange = m.groups[2]!!.range
        return text.substring(0, keyRange.first) + newKey + text.substring(keyRange.last + 1)
    }

    /** Removes the live `<string name="[key]">` entry and its own line; no-op if the key is absent. */
    fun deleteEntry(text: String, key: String): String {
        val m = liveEntryMatches(text).firstOrNull { it.groupValues[2] == key } ?: return text
        var start = m.range.first
        var end = m.range.last + 1
        // Swallow the entry's leading indentation and its trailing newline so no blank line is left behind.
        while (start > 0 && (text[start - 1] == ' ' || text[start - 1] == '\t')) start--
        if (end < text.length && text[end] == '\n') end++
        return text.substring(0, start) + text.substring(end)
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

    /**
     * Escapes a raw string for an Android `strings.xml` value. Rules:
     * - `"` → `\"` and `'` → `\'` — Android requires quotes/apostrophes escaped in unquoted values.
     * - `&`/`<`/`>` → `&amp;`/`&lt;`/`&gt;` — XML entities.
     * - a *leading* `@` or `?` → `\@`/`\?` — otherwise Android reads it as a resource/attr reference.
     */
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

    /** Inverse of [encodeXml]: unescapes entities and leading `\@`/`\?`, then trims whitespace. */
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
