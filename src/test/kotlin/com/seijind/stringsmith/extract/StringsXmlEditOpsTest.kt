package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsXmlEditOpsTest {

    private val sample = """
        <resources>
            <string name="hello">Hello</string>
            <string name="bye">Bye</string>
        </resources>
    """.trimIndent()

    @Test
    fun updateEntryValue_changesOnlyTargetValue() {
        val out = StringsXmlText.updateEntryValue(sample, "hello", "Hi there")
        val entries = StringsXmlText.parseEntries(out)
        assertEquals("Hi there", entries.first { it.key == "hello" }.value)
        assertEquals("Bye", entries.first { it.key == "bye" }.value)
    }

    @Test
    fun updateEntryValue_encodesSpecialChars() {
        val out = StringsXmlText.updateEntryValue(sample, "hello", "A & B <c>")
        assertTrue(out.contains("&amp;"))
        assertEquals("A & B <c>", StringsXmlText.parseEntries(out).first { it.key == "hello" }.value)
    }

    @Test
    fun updateEntryValue_intoEmptyValue() {
        val xml = """<resources><string name="x"></string></resources>"""
        val out = StringsXmlText.updateEntryValue(xml, "x", "filled")
        assertEquals("filled", StringsXmlText.parseEntries(out).first { it.key == "x" }.value)
    }

    @Test
    fun updateEntryValue_missingKeyIsNoOp() {
        assertEquals(sample, StringsXmlText.updateEntryValue(sample, "nope", "x"))
    }

    @Test
    fun renameEntryKey_renamesAndKeepsValue() {
        val out = StringsXmlText.renameEntryKey(sample, "hello", "greeting")
        val entries = StringsXmlText.parseEntries(out)
        assertTrue(entries.any { it.key == "greeting" && it.value == "Hello" })
        assertFalse(entries.any { it.key == "hello" })
    }

    @Test
    fun renameEntryKey_missingKeyIsNoOp() {
        assertEquals(sample, StringsXmlText.renameEntryKey(sample, "nope", "x"))
    }

    @Test
    fun deleteEntry_removesEntryAndLeavesNoBlankLine() {
        val out = StringsXmlText.deleteEntry(sample, "hello")
        val entries = StringsXmlText.parseEntries(out)
        assertEquals(1, entries.size)
        assertEquals("bye", entries[0].key)
        assertFalse(out.contains("hello"))
        assertFalse(out.contains("\n\n"))
    }

    @Test
    fun deleteEntry_missingKeyIsNoOp() {
        assertEquals(sample, StringsXmlText.deleteEntry(sample, "nope"))
    }

    @Test
    fun ops_ignoreCommentedOutEntries() {
        val xml = """
            <resources>
                <!-- <string name="hello">Old</string> -->
                <string name="hello">Live</string>
            </resources>
        """.trimIndent()
        val renamed = StringsXmlText.renameEntryKey(xml, "hello", "greeting")
        // The commented-out line must stay byte-for-byte; only the live entry is renamed.
        assertTrue(renamed.contains("""<!-- <string name="hello">Old</string> -->"""))
        assertTrue(renamed.contains("""<string name="greeting">Live</string>"""))
    }
}
