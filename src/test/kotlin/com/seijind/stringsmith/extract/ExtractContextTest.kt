package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExtractContextTest : BasePlatformTestCase() {

    fun testDetectsLiteralInComposable() {
        val target = detectAt(
            """
            import androidx.compose.runtime.Composable
            @Composable
            fun Greeting() {
                val x = "Hel<caret>lo"
            }
            """.trimIndent(),
            "Greeting.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
        assertEquals("Hello", target.rawValue)
    }

    fun testDetectsLiteralInActivitySubclass() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    val x = "Wel<caret>come"
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testDetectsLiteralInFragmentSuffix() {
        val target = detectAt(
            """
            class HomeFragment : SomeCustomBase() {
                fun foo() {
                    val x = "He<caret>llo"
                }
            }
            """.trimIndent(),
            "HomeFragment.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testGenericKotlinFallback() {
        val target = detectAt(
            """
            object Constants {
                val greeting = "Hel<caret>lo"
            }
            """.trimIndent(),
            "Constants.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.KOTLIN_GENERIC, target!!.kind)
    }

    fun testRejectsTemplateWithExpression() {
        val target = detectAt(
            """
            fun foo(name: String) {
                val x = "Hello ${'$'}name<caret> world"
            }
            """.trimIndent(),
            "Foo.kt"
        )
        assertNull(target)
    }

    fun testCaretAfterClosingQuote() {
        val target = detectAt(
            """
            @androidx.compose.runtime.Composable
            fun Greeting() {
                val x = "Hello"<caret>
            }
            """.trimIndent(),
            "Greeting.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
    }

    fun testCaretBeforeOpeningQuote() {
        val target = detectAt(
            """
            object Constants {
                val x = <caret>"Hello"
            }
            """.trimIndent(),
            "Constants.kt"
        )
        assertNotNull(target)
        assertEquals("Hello", target!!.rawValue)
    }

    fun testReturnsNullWhenCaretNotOnString() {
        val target = detectAt(
            """
            object Constants {
                val x<caret> = 42
            }
            """.trimIndent(),
            "Constants.kt"
        )
        assertNull(target)
    }

    fun testClassifierWorksForWorkerSuffix() {
        val target = detectAt(
            """
            class SyncWorker : ListenableWorker() {
                fun foo() {
                    val x = "Sy<caret>nc"
                }
            }
            """.trimIndent(),
            "SyncWorker.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    private fun detectAt(content: String, fileName: String): ExtractTarget? {
        myFixture.configureByText(fileName, content)
        return ExtractContext.detect(myFixture.file, myFixture.editor)
    }
}
