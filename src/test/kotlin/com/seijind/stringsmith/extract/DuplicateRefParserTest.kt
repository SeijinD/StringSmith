package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateRefParserTest {

    @Test
    fun parsesAndroidReference() {
        val parsed = DuplicateRefParser.parse("R.string.welcome")
        assertEquals(ResourceSystem.ANDROID to "welcome", parsed)
    }

    @Test
    fun parsesComposeMultiplatformReference() {
        val parsed = DuplicateRefParser.parse("Res.string.welcome")
        assertEquals(ResourceSystem.COMPOSE_MULTIPLATFORM to "welcome", parsed)
    }

    @Test
    fun parsesQualifiedReference() {
        val parsed = DuplicateRefParser.parse("com.app.R.string.welcome_screen")
        assertEquals(ResourceSystem.ANDROID to "welcome_screen", parsed)
    }

    @Test
    fun rejectsNonStringReference() {
        assertNull(DuplicateRefParser.parse("R.drawable.icon"))
        assertNull(DuplicateRefParser.parse("foo.bar"))
        assertNull(DuplicateRefParser.parse("R.string"))
    }
}
