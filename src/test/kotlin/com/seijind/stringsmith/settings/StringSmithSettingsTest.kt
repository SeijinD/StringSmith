package com.seijind.stringsmith.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringSmithSettingsTest {

    @Test
    fun excludePatternList_parsesValidLines() {
        val s = StringSmithSettings()
        s.excludePatterns = """
            ^[A-Z_]+$
            https?://.*
            \d+
        """.trimIndent()
        val list = s.excludePatternList()
        assertEquals(3, list.size)
    }

    @Test
    fun excludePatternList_skipsBlankLines() {
        val s = StringSmithSettings()
        s.excludePatterns = "^a$\n\n  \n^b$"
        assertEquals(2, s.excludePatternList().size)
    }

    @Test
    fun excludePatternList_skipsInvalidRegex() {
        val s = StringSmithSettings()
        s.excludePatterns = "^[A-Z]+$\n[unclosed\n^valid$"
        val list = s.excludePatternList()
        assertEquals(2, list.size)
    }

    @Test
    fun matchesExclude_matchesConstantPattern() {
        val s = StringSmithSettings()
        s.excludePatterns = "^[A-Z_]{2,}$"
        assertTrue(s.matchesExclude("API_KEY"))
        assertTrue(s.matchesExclude("MAX_RETRIES"))
    }

    @Test
    fun matchesExclude_doesNotMatchLowercase() {
        val s = StringSmithSettings()
        s.excludePatterns = "^[A-Z_]{2,}$"
        assertFalse(s.matchesExclude("hello"))
    }

    @Test
    fun matchesExclude_matchesUrl() {
        val s = StringSmithSettings()
        s.excludePatterns = "https?://.*"
        assertTrue(s.matchesExclude("https://example.com"))
        assertTrue(s.matchesExclude("http://api.test/v1"))
    }

    @Test
    fun matchesExclude_returnsFalseForEmptyPatterns() {
        val s = StringSmithSettings()
        s.excludePatterns = ""
        assertFalse(s.matchesExclude("anything"))
    }

    @Test
    fun resetToDefaults_clearsCustomValues() {
        val s = StringSmithSettings()
        s.keyPrefix = "custom"
        s.maxKeyLength = 100
        s.resetToDefaults()
        assertEquals("", s.keyPrefix)
        assertEquals(40, s.maxKeyLength)
    }

    @Test
    fun matchesExclude_anyOfMultiplePatterns() {
        val s = StringSmithSettings()
        s.excludePatterns = "^[A-Z_]{2,}$\nhttps?://.*"
        assertTrue(s.matchesExclude("API_KEY"))
        assertTrue(s.matchesExclude("https://x.com"))
        assertFalse(s.matchesExclude("normal text"))
    }

    @Test
    fun excludePatternList_handlesEscapedMetachars() {
        val s = StringSmithSettings()
        s.excludePatterns = "\\d+\n\\.kt$"
        assertEquals(2, s.excludePatternList().size)
        assertTrue(s.matchesExclude("12345"))
    }

    @Test
    fun enumDisplayStrings_areReadable() {
        assertEquals("snake_case", NamingConvention.SNAKE_CASE.toString())
        assertEquals("camelCase", NamingConvention.CAMEL_CASE.toString())
    }

    @Test
    fun activityReplacementStyle_templateFormatsKey() {
        val rendered = ActivityReplacementStyle.GET_STRING.template.format("welcome")
        assertEquals("getString(R.string.welcome)", rendered)
    }

    @Test
    fun composeArgStyle_namedVariant() {
        val rendered = ComposeArgStyle.NAMED.template.format("welcome")
        assertEquals("stringResource(id = R.string.welcome)", rendered)
    }

    @Test
    fun defaults_areReasonable() {
        val s = StringSmithSettings()
        assertEquals("", s.keyPrefix)
        assertEquals(40, s.maxKeyLength)
        assertEquals(2, s.minStringLength)
        assertEquals(NamingConvention.SNAKE_CASE, s.namingConvention)
        assertTrue(s.autoIncludeLocales)
        assertTrue(s.excludePreviewComposables)
        assertTrue(s.trimWhitespace)
        assertFalse(s.sortAfterExtract)
    }
}
