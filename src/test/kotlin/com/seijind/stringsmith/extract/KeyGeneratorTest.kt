package com.seijind.stringsmith.extract

import com.seijind.stringsmith.settings.NamingConvention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyGeneratorTest {

    @Test
    fun suggest_snakeCase_basic() {
        val key = KeyGenerator.suggest("Welcome Screen", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("welcome_screen", key)
    }

    @Test
    fun suggest_camelCase_basic() {
        val key = KeyGenerator.suggest("Welcome Screen", "", NamingConvention.CAMEL_CASE, 40)
        assertEquals("welcomeScreen", key)
    }

    @Test
    fun suggest_snakeCase_withPrefix() {
        val key = KeyGenerator.suggest("Welcome Screen", "app", NamingConvention.SNAKE_CASE, 40)
        assertEquals("app_welcome_screen", key)
    }

    @Test
    fun suggest_camelCase_withPrefix() {
        val key = KeyGenerator.suggest("Welcome Screen", "app", NamingConvention.CAMEL_CASE, 40)
        assertEquals("appWelcomeScreen", key)
    }

    @Test
    fun suggest_trimsTrailingUnderscoreInPrefix() {
        val key = KeyGenerator.suggest("Foo", "app_", NamingConvention.SNAKE_CASE, 40)
        assertEquals("app_foo", key)
    }

    @Test
    fun suggest_stripsSpecialChars() {
        val key = KeyGenerator.suggest("Hello, world!", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("hello_world", key)
    }

    @Test
    fun suggest_collapsesMultipleSeparators() {
        val key = KeyGenerator.suggest("a   --   b", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("a_b", key)
    }

    @Test
    fun suggest_truncatesToMaxLength() {
        val key = KeyGenerator.suggest("This is a very long sentence", "", NamingConvention.SNAKE_CASE, 10)
        assertEquals("this_is_a", key)
    }

    @Test
    fun suggest_trimsTrailingUnderscoreAfterTruncation() {
        val key = KeyGenerator.suggest("foo bar baz", "", NamingConvention.SNAKE_CASE, 8)
        assertFalse(key.endsWith("_"))
    }

    @Test
    fun suggest_emptyValueFallback() {
        val key = KeyGenerator.suggest("!!!", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("label", key)
    }

    @Test
    fun suggest_onlyDigitsKept() {
        val key = KeyGenerator.suggest("Version 2", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("version_2", key)
    }

    @Test
    fun isValidKey_acceptsLowerStart() {
        assertTrue(KeyGenerator.isValidKey("foo_bar"))
    }

    @Test
    fun isValidKey_acceptsUpperStart() {
        assertTrue(KeyGenerator.isValidKey("Foo"))
    }

    @Test
    fun isValidKey_acceptsDigits() {
        assertTrue(KeyGenerator.isValidKey("foo123"))
    }

    @Test
    fun isValidKey_rejectsLeadingDigit() {
        assertFalse(KeyGenerator.isValidKey("1foo"))
    }

    @Test
    fun isValidKey_rejectsLeadingUnderscore() {
        assertFalse(KeyGenerator.isValidKey("_foo"))
    }

    @Test
    fun isValidKey_rejectsSpaces() {
        assertFalse(KeyGenerator.isValidKey("foo bar"))
    }

    @Test
    fun isValidKey_rejectsHyphens() {
        assertFalse(KeyGenerator.isValidKey("foo-bar"))
    }

    @Test
    fun isValidKey_rejectsEmpty() {
        assertFalse(KeyGenerator.isValidKey(""))
    }

    @Test
    fun suggest_unicodeStrippedToEmptyFallsBack() {
        val key = KeyGenerator.suggest("Καλημέρα", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("label", key)
    }

    @Test
    fun suggest_emojiStripped() {
        val key = KeyGenerator.suggest("Hello 🎉 World", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("hello_world", key)
    }

    @Test
    fun suggest_leadingDigitValueGetsLetterPrefix_snake() {
        val key = KeyGenerator.suggest("123 items", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("key_123_items", key)
        assertTrue(KeyGenerator.isValidKey(key))
    }

    @Test
    fun suggest_leadingDigitValueGetsLetterPrefix_camel() {
        val key = KeyGenerator.suggest("123 items", "", NamingConvention.CAMEL_CASE, 40)
        assertEquals("key123Items", key)
        assertTrue(KeyGenerator.isValidKey(key))
    }

    @Test
    fun suggest_onlyDigitsValueGetsLetterPrefix() {
        val key = KeyGenerator.suggest("12345", "", NamingConvention.SNAKE_CASE, 40)
        assertEquals("key_12345", key)
        assertTrue(KeyGenerator.isValidKey(key))
    }

    @Test
    fun suggest_userPrefixAlreadyMakesLeadingDigitValid() {
        val key = KeyGenerator.suggest("123 items", "app", NamingConvention.SNAKE_CASE, 40)
        assertEquals("app_123_items", key)
        assertTrue(KeyGenerator.isValidKey(key))
    }

    @Test
    fun suggest_whitespaceOnlyPrefixSkipped() {
        val key = KeyGenerator.suggest("hello", "   ", NamingConvention.SNAKE_CASE, 40)
        assertEquals("hello", key)
    }

    @Test
    fun suggest_maxLengthOneReturnsAtLeastOneChar() {
        val key = KeyGenerator.suggest("hello world", "", NamingConvention.SNAKE_CASE, 1)
        assertEquals(1, key.length)
    }

    @Test
    fun suggest_zeroMaxLengthCoercedToOne() {
        val key = KeyGenerator.suggest("hello", "", NamingConvention.SNAKE_CASE, 0)
        assertEquals(1, key.length)
    }
}
