package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UnusedStringResourceInspectionTest : BasePlatformTestCase() {

    private fun stringsXml(xml: String, dir: String = "values"): com.intellij.psi.PsiFile {
        val vf = myFixture.addFileToProject("res/$dir/strings.xml", xml).virtualFile
        return PsiManager.getInstance(project).findFile(vf)!!
    }

    fun testFlagsKeyNotReferencedAnywhere() {
        val file = stringsXml(
            """
            <resources>
                <string name="never_used">Foo</string>
            </resources>
            """.trimIndent()
        )
        val problems = UnusedStringResourceInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
        assertTrue(problems[0].descriptionTemplate.contains("never_used"))
    }

    fun testDoesNotFlagWhenReferencedInKotlin() {
        myFixture.addFileToProject(
            "src/MyActivity.kt",
            """
            class MyActivity {
                fun foo() { val x = R.string.welcome }
            }
            """.trimIndent()
        )
        val file = stringsXml(
            """
            <resources>
                <string name="welcome">Welcome</string>
            </resources>
            """.trimIndent()
        )
        val problems = UnusedStringResourceInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testDoesNotFlagWhenReferencedAsCmpRes() {
        myFixture.addFileToProject(
            "src/Greeting.kt",
            """
            import com.example.app.generated.resources.Res
            import com.example.app.generated.resources.welcome
            fun greet() { val x = Res.string.welcome }
            """.trimIndent()
        )
        val file = stringsXml(
            """
            <resources>
                <string name="welcome">Welcome</string>
            </resources>
            """.trimIndent()
        )
        val problems = UnusedStringResourceInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testDoesNotFlagWhenReferencedInLayoutXml() {
        myFixture.addFileToProject(
            "res/layout/main.xml",
            """
            <LinearLayout>
                <TextView android:text="@string/welcome" />
            </LinearLayout>
            """.trimIndent()
        )
        val file = stringsXml(
            """
            <resources>
                <string name="welcome">Welcome</string>
            </resources>
            """.trimIndent()
        )
        val problems = UnusedStringResourceInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertTrue(problems == null || problems.isEmpty())
    }

    fun testFlagsOnlyUnusedAmongMixed() {
        myFixture.addFileToProject(
            "src/MyActivity.kt",
            """
            class MyActivity {
                fun foo() { val x = R.string.welcome }
            }
            """.trimIndent()
        )
        val file = stringsXml(
            """
            <resources>
                <string name="welcome">Welcome</string>
                <string name="legacy_label">Legacy</string>
            </resources>
            """.trimIndent()
        )
        val problems = UnusedStringResourceInspection().checkFile(file, InspectionManager.getInstance(project), false)
        assertNotNull(problems)
        assertEquals(1, problems!!.size)
        assertTrue(problems[0].descriptionTemplate.contains("legacy_label"))
    }
}
