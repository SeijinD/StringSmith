package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsXmlTextTest {

    @Test
    fun parseEntries_singleEntry() {
        val xml = """
            <resources>
                <string name="hello">Hello World</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertEquals("hello", entries[0].key)
        assertEquals("Hello World", entries[0].value)
    }

    @Test
    fun parseEntries_multipleEntries() {
        val xml = """
            <resources>
                <string name="a">A</string>
                <string name="b">B</string>
                <string name="c">C</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(3, entries.size)
        assertEquals(listOf("a", "b", "c"), entries.map { it.key })
    }

    @Test
    fun parseEntries_decodesEscapedQuotes() {
        val xml = """<resources><string name="x">Don\'t click</string></resources>"""
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals("Don't click", entries[0].value)
    }

    @Test
    fun parseEntries_decodesXmlEntities() {
        val xml = """<resources><string name="x">A &amp; B &lt;tag&gt;</string></resources>"""
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals("A & B <tag>", entries[0].value)
    }

    @Test
    fun parseEntries_emptyResources() {
        val xml = "<resources></resources>"
        assertTrue(StringsXmlText.parseEntries(xml).isEmpty())
    }

    @Test
    fun appendEntry_insertsBeforeClosingTag() {
        val xml = """
            <resources>
                <string name="a">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.appendEntry(xml, "b", "B")
        assertTrue(result.contains("""<string name="b">B</string>"""))
        assertTrue(result.indexOf("name=\"a\"") < result.indexOf("name=\"b\""))
        assertTrue(result.indexOf("name=\"b\"") < result.indexOf("</resources>"))
    }

    @Test
    fun appendEntry_escapesSpecialChars() {
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntry(xml, "x", "A & B <tag> 'quote' \"dq\"")
        assertTrue(result.contains("&amp;"))
        assertTrue(result.contains("&lt;tag&gt;"))
        assertTrue(result.contains("\\'quote\\'"))
        assertTrue(result.contains("\\\"dq\\\""))
    }

    @Test
    fun appendEntry_withComment() {
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntry(xml, "x", "v", comment = "from File.kt:42")
        assertTrue(result.contains("<!-- from File.kt:42 -->"))
    }

    @Test
    fun appendEntry_blankCommentSkipped() {
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntry(xml, "x", "v", comment = "  ")
        assertTrue(!result.contains("<!--"))
    }

    @Test
    fun appendEntry_sortsAlphabetically() {
        val xml = """
            <resources>
                <string name="zebra">Z</string>
                <string name="apple">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.appendEntry(xml, "mango", "M", sortAlpha = true)
        val keyOrder = Regex("""name="([^"]+)"""").findAll(result).map { it.groupValues[1] }.toList()
        assertEquals(listOf("apple", "mango", "zebra"), keyOrder)
    }

    @Test
    fun appendEntry_noResourcesTag_buildsOne() {
        val xml = ""
        val result = StringsXmlText.appendEntry(xml, "x", "v")
        assertTrue(result.contains("<resources>"))
        assertTrue(result.contains("</resources>"))
        assertTrue(result.contains("""<string name="x">v</string>"""))
    }

    @Test
    fun encodeXml_basicEscaping() {
        assertEquals("&amp;", StringsXmlText.encodeXml("&"))
        assertEquals("&lt;", StringsXmlText.encodeXml("<"))
        assertEquals("&gt;", StringsXmlText.encodeXml(">"))
        assertEquals("\\'", StringsXmlText.encodeXml("'"))
        assertEquals("\\\"", StringsXmlText.encodeXml("\""))
    }

    @Test
    fun encodeDecode_roundTrip() {
        val input = "Hello & < > ' \" world"
        val encoded = StringsXmlText.encodeXml(input)
        val decoded = StringsXmlText.decodeXml(encoded)
        assertEquals(input, decoded)
    }

    @Test
    fun encodeXml_passesBackslashSourceFormThrough() {
        assertEquals("\\n", StringsXmlText.encodeXml("\\n"))
        assertEquals("path\\\\file", StringsXmlText.encodeXml("path\\\\file"))
    }

    @Test
    fun encodeXml_escapesLeadingAtAndQuestion() {
        assertEquals("\\@type/foo", StringsXmlText.encodeXml("@type/foo"))
        assertEquals("\\?attr/bar", StringsXmlText.encodeXml("?attr/bar"))
        assertEquals("email@example.com", StringsXmlText.encodeXml("email@example.com"))
        assertEquals("why?here?", StringsXmlText.encodeXml("why?here?"))
    }

    @Test
    fun encodeDecode_roundTrip_leadingAtAndQuestion() {
        val cases = listOf(
            "@user mention",
            "?themed value",
            "He said \"hi\" and waved"
        )
        for (input in cases) {
            val encoded = StringsXmlText.encodeXml(input)
            val decoded = StringsXmlText.decodeXml(encoded)
            assertEquals(input, decoded)
        }
    }

    @Test
    fun encodeXml_doesNotEscapeAtOrQuestionMidString() {
        assertEquals("user@host.com", StringsXmlText.encodeXml("user@host.com"))
        assertEquals("really?now", StringsXmlText.encodeXml("really?now"))
    }

    @Test
    fun encodeXml_preservesNewlineAndTabSourceEscapes() {
        assertEquals("Line1\\nLine2", StringsXmlText.encodeXml("Line1\\nLine2"))
        assertEquals("col1\\tcol2", StringsXmlText.encodeXml("col1\\tcol2"))
    }

    @Test
    fun encodeXml_combinesEscapes() {
        val input = "Hello, \"world\" & <b>good</b>"
        val expected = "Hello, \\\"world\\\" &amp; &lt;b&gt;good&lt;/b&gt;"
        assertEquals(expected, StringsXmlText.encodeXml(input))
    }

    @Test
    fun decodeXml_reversesLeadingAt() {
        assertEquals("@user", StringsXmlText.decodeXml("\\@user"))
    }

    @Test
    fun decodeXml_reversesLeadingQuestion() {
        assertEquals("?attr", StringsXmlText.decodeXml("\\?attr"))
    }

    @Test
    fun decodeXml_doesNotTouchMidStringAtOrQuestion() {
        assertEquals("a@b?c", StringsXmlText.decodeXml("a@b?c"))
    }

    @Test
    fun encodeXml_emptyAndSingleChar() {
        assertEquals("", StringsXmlText.encodeXml(""))
        assertEquals("\\@", StringsXmlText.encodeXml("@"))
        assertEquals("\\?", StringsXmlText.encodeXml("?"))
        assertEquals("\\\"", StringsXmlText.encodeXml("\""))
    }

    @Test
    fun encodeDecode_roundTrip_combinedCases() {
        val cases = listOf(
            "Hello & <b>world</b>",
            "It's a \"test\"",
            "@reference",
            "?themed",
            "Line1\\nLine2",
            "Mixed: @start, mid@, end?",
            "Empty: ",
            ""
        )
        for (input in cases) {
            val encoded = StringsXmlText.encodeXml(input)
            val decoded = StringsXmlText.decodeXml(encoded)
            assertEquals("Round-trip failed for: '$input'", input.trim(), decoded)
        }
    }

    @Test
    fun parseEntries_multilineValue() {
        val xml = """
            <resources>
                <string name="multi">Line one
            Line two</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertTrue(entries[0].value.contains("Line one"))
        assertTrue(entries[0].value.contains("Line two"))
    }

    @Test
    fun parseEntries_ignoresPluralsAndArrays() {
        val xml = """
            <resources>
                <string name="ok">OK</string>
                <plurals name="apples"><item quantity="one">apple</item></plurals>
                <string-array name="colors"><item>red</item></string-array>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertEquals("ok", entries[0].key)
    }

    @Test
    fun parseEntries_handlesTranslatableAttribute() {
        val xml = """
            <resources>
                <string name="api_key" translatable="false">XYZ</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertEquals("api_key", entries[0].key)
    }

    @Test
    fun parseEntries_handlesNameNotFirstAttribute() {
        val xml = """
            <resources>
                <string translatable="false" name="api_key">XYZ</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertEquals("api_key", entries[0].key)
        assertEquals("XYZ", entries[0].value)
    }

    @Test
    fun parseEntries_handlesSingleQuotedName() {
        val xml = "<resources><string name='hello'>Hello</string></resources>"
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(1, entries.size)
        assertEquals("hello", entries[0].key)
        assertEquals("Hello", entries[0].value)
    }

    @Test
    fun parseEntries_stringArrayNotMistakenForString() {
        val xml = """
            <resources>
                <string-array name="colors"><item>red</item><item>blue</item></string-array>
                <string name="real">Real</string>
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(listOf("real"), entries.map { it.key })
    }

    @Test
    fun appendEntry_sortsAlphabeticallyKeepsCommentsBeforeEntries() {
        val xml = """
            <resources>
                <!-- from Z.kt -->
                <string name="zebra">Z</string>
                <string name="apple">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.appendEntry(xml, "mango", "M", sortAlpha = true)
        val keyOrder = Regex("""name="([^"]+)"""").findAll(result).map { it.groupValues[1] }.toList()
        assertEquals(listOf("apple", "mango", "zebra"), keyOrder)
    }

    @Test
    fun appendEntries_insertsAllBeforeClosingTagInOrder() {
        val xml = "<resources>\n    <string name=\"a\">A</string>\n</resources>"
        val result = StringsXmlText.appendEntries(xml, listOf(StringEntryDraft("b", "B"), StringEntryDraft("c", "C")))
        val order = Regex("""name="([^"]+)"""").findAll(result).map { it.groupValues[1] }.toList()
        assertEquals(listOf("a", "b", "c"), order)
        assertTrue(result.indexOf("name=\"c\"") < result.indexOf("</resources>"))
    }

    @Test
    fun appendEntries_emptyListReturnsInputUnchanged() {
        val xml = "<resources></resources>"
        assertEquals(xml, StringsXmlText.appendEntries(xml, emptyList()))
    }

    @Test
    fun appendEntries_writesPerEntryComment() {
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntries(
            xml,
            listOf(StringEntryDraft("a", "A", "from A.kt:1"), StringEntryDraft("b", "B"))
        )
        assertTrue(result.contains("<!-- from A.kt:1 -->"))
        assertEquals(1, Regex("<!--").findAll(result).count()) // only the entry that had a comment
    }

    @Test
    fun appendEntries_matchesRepeatedAppendEntry() {
        val xml = "<resources>\n    <string name=\"a\">A</string>\n</resources>"
        val batched = StringsXmlText.appendEntries(xml, listOf(StringEntryDraft("m", "M"), StringEntryDraft("z", "Z")), sortAlpha = true)
        val sequential = StringsXmlText.appendEntry(
            StringsXmlText.appendEntry(xml, "m", "M", sortAlpha = true),
            "z", "Z", sortAlpha = true
        )
        assertEquals(sequential, batched)
    }

    @Test
    fun parseEntries_ignoresCommentedOutString() {
        val xml = """
            <resources>
                <string name="live">Live</string>
                <!--    <string name="dead">Dead translation</string>-->
            </resources>
        """.trimIndent()
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(listOf("live"), entries.map { it.key })
    }

    @Test
    fun sortStringEntries_doesNotShuffleLiveStringsIntoComments() {
        // Mirrors the real bug: a commented-out <string> sits between live entries. Sorting must leave
        // the comment (and its dead entry) untouched and must NOT push a live entry inside the comment.
        val xml = "<resources>\n" +
            "    <string name=\"zebra\">Z</string>\n" +
            "    <!--    <string name=\"old_dead\">Old</string>-->\n" +
            "    <string name=\"apple\">A</string>\n" +
            "</resources>"
        val result = StringsXmlText.sortStringEntries(xml)
        // Live entries reordered alphabetically.
        assertTrue(result.indexOf("name=\"apple\"") < result.indexOf("name=\"zebra\""))
        // The dead entry is still commented out, verbatim, exactly once.
        assertTrue("dead entry must stay commented", result.contains("<!--    <string name=\"old_dead\">Old</string>-->"))
        // No live entry was swallowed: both live keys are parseable as live entries.
        assertEquals(listOf("apple", "zebra"), StringsXmlText.parseEntries(result).map { it.key })
    }

    @Test
    fun sortStringEntries_commentedStringNotCountedTowardSortThreshold() {
        // One live + one commented entry => fewer than 2 live entries => returned unchanged.
        val xml = "<resources>\n" +
            "    <string name=\"only_live\">L</string>\n" +
            "    <!-- <string name=\"dead\">D</string> -->\n" +
            "</resources>"
        assertEquals(xml, StringsXmlText.sortStringEntries(xml))
    }

    @Test
    fun sortStringEntries_singleEntryReturnsAsIs() {
        val xml = """
            <resources>
                <string name="only">X</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.sortStringEntries(xml)
        assertTrue(result.contains("""<string name="only">X</string>"""))
    }

    @Test
    fun sortStringEntries_preservesCommentWithEntry() {
        val xml = """
            <resources>
                <!-- from Z.kt -->
                <string name="zebra">Z</string>
                <string name="apple">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.sortStringEntries(xml)
        val appleIdx = result.indexOf("name=\"apple\"")
        val zebraIdx = result.indexOf("name=\"zebra\"")
        assertTrue("apple should be before zebra", appleIdx < zebraIdx)
    }

    @Test
    fun sortStringEntries_preservesPluralsAndArrays() {
        val xml = """
            <resources>
                <string name="zebra">Z</string>
                <plurals name="apples"><item quantity="one">apple</item></plurals>
                <string-array name="colors"><item>red</item></string-array>
                <string name="apple">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.sortStringEntries(xml)
        assertTrue("plurals must survive", result.contains("""<plurals name="apples">"""))
        assertTrue("string-array must survive", result.contains("""<string-array name="colors">"""))
        assertTrue(result.indexOf("name=\"apple\"") < result.indexOf("name=\"zebra\""))
    }

    @Test
    fun sortStringEntries_doesNotReindentMultilineValue() {
        val xml = "<resources>\n" +
            "    <string name=\"zebra\">Z</string>\n" +
            "    <string name=\"multi\">Line one\nLine two</string>\n" +
            "</resources>"
        val result = StringsXmlText.sortStringEntries(xml)
        assertTrue("multi-line value must stay verbatim", result.contains("Line one\nLine two"))
    }

    @Test
    fun sortStringEntries_preservesTrailingComment() {
        val xml = """
            <resources>
                <string name="zebra">Z</string>
                <string name="apple">A</string>
                <!-- end of file -->
            </resources>
        """.trimIndent()
        val result = StringsXmlText.sortStringEntries(xml)
        assertTrue("trailing comment must survive", result.contains("<!-- end of file -->"))
        assertTrue(result.indexOf("name=\"apple\"") < result.indexOf("name=\"zebra\""))
    }

    @Test
    fun sortStringEntries_doesNotDuplicateSectionComment() {
        val xml = """
            <resources>
                <!-- Settings -->
                <string name="zebra">Z</string>
                <string name="apple">A</string>
            </resources>
        """.trimIndent()
        val result = StringsXmlText.sortStringEntries(xml)
        assertEquals(1, Regex("<!-- Settings -->").findAll(result).count())
    }

    @Test
    fun appendEntry_commentWithDoubleHyphenStaysValidXml() {
        // A source filename like `foo--bar.kt` must not produce `<!-- … foo--bar.kt … -->` (XML 1.0
        // forbids `--` inside a comment), which would corrupt strings.xml.
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntry(xml, "x", "v", comment = "from foo--bar.kt:42")
        val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).find(result)!!.value
        // No `--` survives inside the comment body (only the opening `<!--`/closing `-->` markers).
        val body = comment.removePrefix("<!--").removeSuffix("-->")
        assertTrue("comment body must not contain '--': $body", !body.contains("--"))
        assertTrue("comment must not end in '-'", !body.trimEnd().endsWith("-") || body.endsWith(" "))
    }

    @Test
    fun appendEntry_commentTrailingHyphenPadded() {
        val xml = "<resources></resources>"
        val result = StringsXmlText.appendEntry(xml, "x", "v", comment = "from weird-")
        // Closing marker must not glue onto a trailing hyphen (`--->` is illegal).
        assertTrue(!result.contains("--->"))
    }

    @Test
    fun parseEntries_ignoresEntryStraddlingCommentBoundary() {
        // An entry whose opening tag is live but whose close falls inside a comment must be treated as
        // dead, not parsed with a truncated/garbage value.
        val xml = "<resources>\n" +
            "    <string name=\"live\">Live</string>\n" +
            "    <string name=\"straddle\">val <!-- oops</string> still comment -->\n" +
            "</resources>"
        val entries = StringsXmlText.parseEntries(xml)
        assertEquals(listOf("live"), entries.map { it.key })
    }
}
