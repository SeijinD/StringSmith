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

    fun testTemplateWithExpression_genericKotlinReturnsNull() {
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

    fun testTemplateWithExpression_composableExtractsArgs() {
        val target = detectAt(
            """
            import androidx.compose.runtime.Composable
            @Composable
            fun Greet(name: String) {
                val x = "Hello ${'$'}name<caret> world"
            }
            """.trimIndent(),
            "Greet.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
        assertEquals("Hello %1\$s world", target.rawValue)
        assertEquals(listOf("name"), target.formatArgs)
    }

    fun testTemplateWithBlockExpression_activityExtractsArgs() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo(user: User) {
                    val x = "Welcome, ${'$'}{user.name}<caret>!"
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
        assertEquals("Welcome, %1\$s!", target.rawValue)
        assertEquals(listOf("user.name"), target.formatArgs)
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

    fun testIsInsidePreviewComposable_true() {
        val target = detectAt(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            @Preview
            @Composable
            fun GreetingPreview() {
                val x = "Hel<caret>lo"
            }
            """.trimIndent(),
            "GreetingPreview.kt"
        )
        assertNotNull(target)
        assertTrue(ExtractContext.isInsidePreviewComposable(target!!))
    }

    fun testIsInsidePreviewComposable_false() {
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
        assertFalse(ExtractContext.isInsidePreviewComposable(target!!))
    }

    fun testDetectsComposableInsideSetContentLambdaInActivity() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        val title = "Hel<caret>lo"
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
    }

    fun testActivityScopeOutsideSetContent() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    val title = "Hel<caret>lo"
                    setContent {
                        val inner = "World"
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testDetectsComposableInsideNavComposableLambda() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun build(navBuilder: Any) {
                    composable("home") {
                        val title = "Wel<caret>come"
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
    }

    fun testOnClickLambdaInsideComposableIsAndroidClass() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    setContent {
                        Button(onClick = {
                            val msg = "Hel<caret>lo"
                        }) {}
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testNestedColumnInsideSetContentIsComposable() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    setContent {
                        Column {
                            val msg = "Hel<caret>lo"
                        }
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
    }

    fun testButtonContentLambdaInsideSetContentIsComposable() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    setContent {
                        Button(onClick = {}) {
                            val label = "Cli<caret>ck me"
                        }
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
    }

    fun testOnClickDeepInsideNestedComposableIsAndroidClass() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    setContent {
                        Column {
                            Button(onClick = {
                                val msg = "Hel<caret>lo"
                            }) {}
                        }
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testUnknownTrailingLambdaOutsideEntryPointIsAndroidClass() {
        val target = detectAt(
            """
            class MainActivity : AppCompatActivity() {
                fun foo() {
                    runOnUiThread {
                        val msg = "Hel<caret>lo"
                    }
                }
            }
            """.trimIndent(),
            "MainActivity.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.ANDROID_CLASS, target!!.kind)
    }

    fun testCustomWrapperWithoutComposeClasspathFallsBack() {
        val target = detectAt(
            """
            fun NavGraphBuilder.kinoHomeScreen() {
                screenViewComposable<KinoHomeRoute> {
                    val title = "Hel<caret>lo"
                }
            }
            """.trimIndent(),
            "KinoHomeScreen.kt"
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.KOTLIN_GENERIC, target!!.kind)
    }

    fun testCustomWrapperViaSettingsIsComposable() {
        val settings = com.seijind.stringsmith.settings.StringSmithSettings.getInstance()
        val previous = settings.customComposableLambdaFunctions
        settings.customComposableLambdaFunctions = "screenViewComposable"
        try {
            val target = detectAt(
                """
                fun NavGraphBuilder.kinoHomeScreen() {
                    screenViewComposable<KinoHomeRoute> {
                        val title = "Hel<caret>lo"
                    }
                }
                """.trimIndent(),
                "KinoHomeScreen.kt"
            )
            assertNotNull(target)
            assertEquals(ExtractContextKind.COMPOSABLE, target!!.kind)
        } finally {
            settings.customComposableLambdaFunctions = previous
        }
    }

    fun testDetectsXmlAttributeValue() {
        myFixture.configureByText(
            "layout.xml",
            """
            <LinearLayout>
                <TextView android:text="Wel<caret>come" />
            </LinearLayout>
            """.trimIndent()
        )
        val target = ExtractContext.detect(myFixture.file, myFixture.editor)
        assertNotNull(target)
        assertEquals(ExtractContextKind.XML_LAYOUT, target!!.kind)
        assertEquals("Welcome", target.rawValue)
    }

    fun testRejectsXmlStringReference() {
        myFixture.configureByText(
            "layout.xml",
            """
            <TextView android:text="@string/<caret>welcome" />
            """.trimIndent()
        )
        val target = ExtractContext.detect(myFixture.file, myFixture.editor)
        assertNull(target)
    }

    fun testRejectsXmlAttrReference() {
        myFixture.configureByText(
            "layout.xml",
            """
            <TextView android:textColor="?attr/<caret>textColorPrimary" />
            """.trimIndent()
        )
        val target = ExtractContext.detect(myFixture.file, myFixture.editor)
        assertNull(target)
    }

    private fun detectAt(content: String, fileName: String): ExtractTarget? {
        myFixture.configureByText(fileName, content)
        return ExtractContext.detect(myFixture.file, myFixture.editor)
    }
}
