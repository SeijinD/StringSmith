package com.seijind.stringsmith.inspections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSpecTest {

    @Test
    fun specifiers_implicitPositions() {
        val specs = FormatSpec.specifiers("Hello %s, you have %d messages")
        assertEquals(listOf(FormatSpecifier(1, 's'), FormatSpecifier(2, 'd')), specs)
    }

    @Test
    fun specifiers_explicitPositions() {
        val specs = FormatSpec.specifiers("%2\$d before %1\$s")
        assertEquals(listOf(FormatSpecifier(2, 'd'), FormatSpecifier(1, 's')), specs)
    }

    @Test
    fun specifiers_withWidthAndPrecisionAndFlags() {
        val specs = FormatSpec.specifiers("Total: %,.2f (%03d)")
        assertEquals(listOf(FormatSpecifier(1, 'f'), FormatSpecifier(2, 'd')), specs)
    }

    @Test
    fun specifiers_escapedPercentIsNotAnArgument() {
        assertTrue(FormatSpec.specifiers("50%% off").isEmpty())
        assertTrue(FormatSpec.specifiers("50% off").isEmpty())
    }

    @Test
    fun specifiers_newlineConsumesNoArgument() {
        assertTrue(FormatSpec.specifiers("line one%nline two").isEmpty())
    }

    @Test
    fun analyze_repeatedPositionCountsOnce() {
        assertNull(FormatSpec.analyze("%1\$s and %1\$s", "%1\$s"))
    }

    @Test
    fun analyze_identicalIsOk() {
        assertNull(FormatSpec.analyze("Hello %1\$s, %2\$d msgs", "Hallo %1\$s, %2\$d Nachrichten"))
    }

    @Test
    fun analyze_noArgumentsIsOk() {
        assertNull(FormatSpec.analyze("Settings", "Einstellungen"))
    }

    @Test
    fun analyze_missingArgumentIsCountMismatch() {
        val m = FormatSpec.analyze("Hello %1\$s, you have %2\$d messages", "Hallo %1\$s")
        assertEquals(FormatMismatch.Count(2, 1), m)
    }

    @Test
    fun analyze_extraArgumentIsCountMismatch() {
        val m = FormatSpec.analyze("Hello %s", "Hallo %s %s")
        assertEquals(FormatMismatch.Count(1, 2), m)
    }

    @Test
    fun analyze_typeMismatchAtPosition() {
        val m = FormatSpec.analyze("You have %1\$d messages", "Sie haben %1\$s Nachrichten")
        assertEquals(FormatMismatch.Type(1, 'd', 's'), m)
    }

    @Test
    fun analyze_implicitOrderTypeMismatch() {
        val m = FormatSpec.analyze("%s = %d", "%s = %s")
        assertEquals(FormatMismatch.Type(2, 'd', 's'), m)
    }

    @Test
    fun analyze_translationAddsArgsToPlainDefault() {
        val m = FormatSpec.analyze("Welcome", "Willkommen %s")
        assertEquals(FormatMismatch.Count(0, 1), m)
    }
}
