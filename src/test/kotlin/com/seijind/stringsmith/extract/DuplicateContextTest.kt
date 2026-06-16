package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DuplicateContextTest : BasePlatformTestCase() {

    fun testDetectsFromAndroidCodeReference() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>"""
        )
        myFixture.configureByText(
            "Main.kt",
            """
            fun foo() {
                val id = R.string.wel<caret>come
            }
            """.trimIndent()
        )
        val source = DuplicateContext.detect(project, myFixture.file, myFixture.editor)
        assertNotNull(source)
        assertEquals("welcome", source!!.key)
        assertEquals(ResourceSystem.ANDROID, source.system)
        assertEquals("Welcome", source.defaultValue)
        assertNotNull(source.codeRef)
    }

    private fun detectXml(content: String): DuplicateSource? {
        val xml = myFixture.addFileToProject("app/src/main/res/values/strings.xml", content)
        myFixture.configureFromExistingVirtualFile(xml.virtualFile)
        return DuplicateContext.detect(project, myFixture.file, myFixture.editor)
    }

    fun testDetectsFromXmlEntry() {
        val source = detectXml("""<resources><string name="wel<caret>come">Welcome</string></resources>""")
        assertNotNull(source)
        assertEquals("welcome", source!!.key)
        assertEquals("Welcome", source.defaultValue)
        assertNull(source.codeRef)
    }

    fun testDetectsFromXmlValueText() {
        val source = detectXml("""<resources><string name="welcome">Wel<caret>come</string></resources>""")
        assertNotNull(source)
        assertEquals("welcome", source!!.key)
        assertEquals("Welcome", source.defaultValue)
        assertNull(source.codeRef)
    }

    fun testXmlCaretOnTagNameWord() {
        val s = detectXml("""<resources><str<caret>ing name="welcome">Welcome</string></resources>""")
        assertEquals("welcome", s?.key)
    }

    fun testXmlCaretAtEndOfValue() {
        val s = detectXml("""<resources><string name="welcome">Welcome<caret></string></resources>""")
        assertEquals("welcome", s?.key)
    }

    fun testXmlCaretAfterClosingTag() {
        val s = detectXml("""<resources><string name="welcome">Welcome</string><caret></resources>""")
        assertEquals("welcome", s?.key)
    }

    fun testKotlinCaretOnStringWord() {
        myFixture.addFileToProject("app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>""")
        myFixture.configureByText("Main.kt", """fun f() { val id = stringResource(R.str<caret>ing.welcome) }""")
        val s = DuplicateContext.detect(project, myFixture.file, myFixture.editor)
        assertEquals("welcome", s?.key)
    }

    fun testKotlinCaretAtEndOfCall() {
        myFixture.addFileToProject("app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>""")
        myFixture.configureByText("Main.kt", """fun f() { val id = stringResource(R.string.welcome<caret>) }""")
        val s = DuplicateContext.detect(project, myFixture.file, myFixture.editor)
        assertEquals("welcome", s?.key)
    }

    fun testMapsLocaleValues() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>"""
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-de/strings.xml",
            """<resources><string name="welcome">Willkommen</string></resources>"""
        )
        myFixture.configureByText(
            "Main.kt",
            """
            fun foo() {
                val id = R.string.wel<caret>come
            }
            """.trimIndent()
        )
        val source = DuplicateContext.detect(project, myFixture.file, myFixture.editor)!!
        val deValue = source.localeValues.entries.first { it.key.path.contains("values-de") }.value
        assertEquals("Willkommen", deValue)
        assertTrue("A translated locale must not be flagged untranslated", source.untranslatedLocales.isEmpty())
    }

    fun testSkipsLocaleMissingKey() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>"""
        )
        myFixture.addFileToProject(
            "app/src/main/res/values-de/strings.xml",
            """<resources><string name="other">Andere</string></resources>"""
        )
        myFixture.configureByText(
            "Main.kt",
            """
            fun foo() {
                val id = R.string.wel<caret>come
            }
            """.trimIndent()
        )
        val source = DuplicateContext.detect(project, myFixture.file, myFixture.editor)!!
        // values-de does not translate "welcome": it must NOT receive a fabricated default-value copy,
        // it is reported as untranslated instead so the duplicate mirrors the source key's coverage.
        assertTrue(
            "Untranslated locale must not be in localeValues",
            source.localeValues.keys.none { it.path.contains("values-de") }
        )
        assertTrue(
            "Untranslated locale must be reported as skipped",
            source.untranslatedLocales.any { it.path.contains("values-de") }
        )
    }

    fun testReturnsNullWhenNotOnReference() {
        myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="welcome">Welcome</string></resources>"""
        )
        myFixture.configureByText(
            "Main.kt",
            """
            fun foo() {
                val x<caret> = 42
            }
            """.trimIndent()
        )
        assertNull(DuplicateContext.detect(project, myFixture.file, myFixture.editor))
    }
}
