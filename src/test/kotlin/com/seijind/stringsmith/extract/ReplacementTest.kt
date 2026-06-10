package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReplacementTest : BasePlatformTestCase() {

    private fun targetAt(content: String, fileName: String = "Foo.kt"): ExtractTarget {
        myFixture.configureByText(fileName, content)
        return ExtractContext.detect(myFixture.file, myFixture.editor)!!
    }

    fun testReferenceFor_composableNoArgs() {
        val target = targetAt(
            """
            import androidx.compose.runtime.Composable
            @Composable
            fun Greet() { val x = "He<caret>llo" }
            """.trimIndent(),
            "Greet.kt"
        )
        assertEquals("stringResource(R.string.hello)", Replacement.referenceFor(target, "hello"))
    }

    fun testReferenceFor_composableWithFormatArgs() {
        val target = targetAt(
            """
            import androidx.compose.runtime.Composable
            @Composable
            fun Greet(name: String) { val x = "Hello ${'$'}name<caret>" }
            """.trimIndent(),
            "Greet.kt"
        )
        assertEquals("stringResource(R.string.hello_s, name)", Replacement.referenceFor(target, "hello_s"))
    }

    fun testReferenceFor_activityNoArgs() {
        val target = targetAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() { val x = "Wel<caret>come" }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertEquals("getString(R.string.welcome)", Replacement.referenceFor(target, "welcome"))
    }

    fun testReferenceFor_activityWithFormatArgs() {
        val target = targetAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo(user: User) { val x = "Welcome, ${'$'}{user.name}<caret>!" }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertEquals(
            "getString(R.string.welcome_s, user.name)",
            Replacement.referenceFor(target, "welcome_s")
        )
    }

    fun testReferenceFor_genericKotlinNoArgs() {
        val target = targetAt(
            """
            object Constants { val greeting = "Hel<caret>lo" }
            """.trimIndent(),
            "Constants.kt"
        )
        assertEquals("R.string.hello", Replacement.referenceFor(target, "hello"))
    }
}
