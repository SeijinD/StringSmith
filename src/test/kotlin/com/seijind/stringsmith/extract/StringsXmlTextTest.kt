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
}
