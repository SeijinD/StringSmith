package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DuplicateStringValueInspectionTest : BasePlatformTestCase() {

    private fun configureStringsXml(xml: String, dir: String = "values"): com.intellij.psi.PsiFile {
        val vf = myFixture.addFileToProject("res/$dir/strings.xml", xml).virtualFile
        return PsiManager.getInstance(project).findFile(vf)!!
    }

    fun testFlagsDuplicateValueAcrossKeys() {
        val file = configureStringsXml(
            """
            <resources>
                <string name="login_title">Welcome</string>
                <string name="onboarding_greeting">Welcome</string>
            </resources>
            """.trimIndent()
        )
        val problems = DuplicateStringValueInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
        assertTrue(problems[0].descriptionTemplate.contains("login_title"))
    }

    fun testDoesNotFlagUniqueValues() {
        val file = configureStringsXml(
            """
            <resources>
                <string name="welcome">Welcome</string>
                <string name="bye">Goodbye</string>
            </resources>
            """.trimIndent()
        )
        val problems = DuplicateStringValueInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testFlagsThreeWayDuplicate() {
        val file = configureStringsXml(
            """
            <resources>
                <string name="a">Hi</string>
                <string name="b">Hi</string>
                <string name="c">Hi</string>
            </resources>
            """.trimIndent()
        )
        val problems = DuplicateStringValueInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertNotNull(problems)
        assertEquals(2, problems!!.size)
    }

    fun testIgnoresEmptyValues() {
        val file = configureStringsXml(
            """
            <resources>
                <string name="a"></string>
                <string name="b"></string>
            </resources>
            """.trimIndent()
        )
        val problems = DuplicateStringValueInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testWorksInLocaleVariant() {
        val file = configureStringsXml(
            """
            <resources>
                <string name="hello_a">Καλημέρα</string>
                <string name="hello_b">Καλημέρα</string>
            </resources>
            """.trimIndent(),
            dir = "values-el"
        )
        val problems = DuplicateStringValueInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
    }
}
