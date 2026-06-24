package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FormatStringMismatchInspectionTest : BasePlatformTestCase() {

    // `$` escaped so the Kotlin compiler doesn't read `$s` / `$d` as string templates.
    private val D = "$"

    private fun addStringsXml(relativeDir: String, xml: String): PsiFile {
        val vf = myFixture.addFileToProject("$relativeDir/strings.xml", xml).virtualFile
        return PsiManager.getInstance(project).findFile(vf)!!
    }

    private fun check(file: PsiFile) =
        FormatStringMismatchInspection().checkFile(file, InspectionManager.getInstance(project), false)

    // ---- Android (res/values*) ----

    fun testAndroidFlagsMissingArgument() {
        addStringsXml("res/values", """<resources><string name="greeting">Hello %1${D}s, %2${D}d msgs</string></resources>""")
        val locale = addStringsXml("res/values-de", """<resources><string name="greeting">Hallo %1${D}s</string></resources>""")
        val problems = check(locale)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
        assertTrue(problems[0].descriptionTemplate.contains("greeting"))
    }

    fun testAndroidFlagsTypeMismatch() {
        addStringsXml("res/values", """<resources><string name="count">You have %1${D}d items</string></resources>""")
        val locale = addStringsXml("res/values-fr", """<resources><string name="count">Vous avez %1${D}s articles</string></resources>""")
        val problems = check(locale)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
    }

    fun testAndroidMatchingFormatIsClean() {
        addStringsXml("res/values", """<resources><string name="greeting">Hello %1${D}s, %2${D}d msgs</string></resources>""")
        val locale = addStringsXml("res/values-de", """<resources><string name="greeting">Hallo %1${D}s, %2${D}d Nachrichten</string></resources>""")
        val problems = check(locale)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testDefaultFileItselfNotInspected() {
        val default = addStringsXml("res/values", """<resources><string name="g">Hello %1${D}s %2${D}d</string></resources>""")
        addStringsXml("res/values-de", """<resources><string name="g">Hallo %1${D}s</string></resources>""")
        val problems = check(default)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testKeyAbsentFromDefaultIsSkipped() {
        addStringsXml("res/values", """<resources><string name="other">Hi</string></resources>""")
        val locale = addStringsXml("res/values-de", """<resources><string name="lonely">Hallo %s</string></resources>""")
        val problems = check(locale)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testEscapedPercentNotFlagged() {
        addStringsXml("res/values", """<resources><string name="sale">50%% off</string></resources>""")
        val locale = addStringsXml("res/values-de", """<resources><string name="sale">50%% Rabatt</string></resources>""")
        val problems = check(locale)
        assertTrue(problems == null || problems.isEmpty())
    }

    // ---- Compose Multiplatform (composeResources/values*) ----

    fun testCmpFlagsMissingArgument() {
        addStringsXml("composeResources/values", """<resources><string name="greeting">Hello %1${D}s, %2${D}d msgs</string></resources>""")
        val locale = addStringsXml("composeResources/values-de", """<resources><string name="greeting">Hallo %1${D}s</string></resources>""")
        val problems = check(locale)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
        assertTrue(problems[0].descriptionTemplate.contains("greeting"))
    }

    fun testCmpMatchingFormatIsClean() {
        addStringsXml("composeResources/values", """<resources><string name="count">%1${D}d items</string></resources>""")
        val locale = addStringsXml("composeResources/values-fr", """<resources><string name="count">%1${D}d articles</string></resources>""")
        val problems = check(locale)
        assertTrue(problems == null || problems.isEmpty())
    }
}
