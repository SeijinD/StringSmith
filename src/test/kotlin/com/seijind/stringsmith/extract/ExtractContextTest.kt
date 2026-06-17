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
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <LinearLayout>
                <TextView android:text="Wel<caret>come" />
            </LinearLayout>
            """.trimIndent()
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.XML_LAYOUT, target!!.kind)
        assertEquals("Welcome", target.rawValue)
    }

    fun testDetectsXmlHintAttribute() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <EditText android:hint="Enter <caret>name" />
            """.trimIndent()
        )
        assertNotNull(target)
        assertEquals(ExtractContextKind.XML_LAYOUT, target!!.kind)
        assertEquals("Enter name", target.rawValue)
    }

    fun testRejectsXmlLayoutEnumAttribute() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <LinearLayout android:layout_width="match_<caret>parent" />
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsXmlDimensionAttribute() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <TextView android:textSize="16<caret>sp" />
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsXmlToolsAttribute() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <TextView tools:text="Sample <caret>text" />
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsXmlStringReference() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <TextView android:text="@string/<caret>welcome" />
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsXmlAttrReference() {
        val target = detectInResXml(
            "res/layout/layout.xml",
            """
            <TextView android:textColor="?attr/<caret>textColorPrimary" />
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsXmlOutsideResDir() {
        val target = detectInResXml(
            "config/layout.xml",
            """
            <LinearLayout>
                <TextView android:text="Wel<caret>come" />
            </LinearLayout>
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testRejectsValuesXmlAttribute() {
        val target = detectInResXml(
            "res/values/strings.xml",
            """
            <resources>
                <string name="wel<caret>come">Welcome</string>
            </resources>
            """.trimIndent()
        )
        assertNull(target)
    }

    fun testIgnorableForInspection_annotationArgument() {
        assertTrue(
            ignorableAt(
                """
                @Deprecated("do not <caret>use this")
                fun old() {}
                """.trimIndent(),
                "Old.kt"
            )
        )
    }

    fun testIgnorableForInspection_constValue() {
        assertTrue(
            ignorableAt(
                """
                object C { const val TAG = "Main<caret>Activity" }
                """.trimIndent(),
                "C.kt"
            )
        )
    }

    fun testIgnorableForInspection_loggingArgument() {
        assertTrue(
            ignorableAt(
                """
                fun f() { Log.d(TAG, "loading <caret>data") }
                """.trimIndent(),
                "F.kt"
            )
        )
    }

    fun testIgnorableForInspection_timberArgument() {
        assertTrue(
            ignorableAt(
                """
                fun f(e: Throwable) { Timber.e(e, "sync <caret>failed") }
                """.trimIndent(),
                "F.kt"
            )
        )
    }

    fun testIgnorableForInspection_plainUiStringIsNotIgnorable() {
        assertFalse(
            ignorableAt(
                """
                fun f() { val x = "Wel<caret>come" }
                """.trimIndent(),
                "F.kt"
            )
        )
    }

    fun testIgnorableForInspection_loggingRespectsToggleOff() {
        val settings = com.seijind.stringsmith.settings.StringSmithSettings.getInstance()
        val previous = settings.ignoreLoggingStrings
        settings.ignoreLoggingStrings = false
        try {
            assertFalse(
                ignorableAt(
                    """
                    fun f() { Log.d(TAG, "loading <caret>data") }
                    """.trimIndent(),
                    "F.kt"
                )
            )
        } finally {
            settings.ignoreLoggingStrings = previous
        }
    }

    private fun ignorableAt(content: String, fileName: String): Boolean {
        myFixture.configureByText(fileName, content)
        val offset = myFixture.editor.caretModel.offset
        val expr = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(offset),
            org.jetbrains.kotlin.psi.KtStringTemplateExpression::class.java,
            false
        )!!
        return ExtractContext.isIgnorableForInspection(expr)
    }

    // Real project-relative path (configureByText cannot set one) so res/<type>/ detection applies.
    private fun detectInResXml(path: String, content: String): ExtractTarget? {
        val caret = content.indexOf("<caret>")
        val vf = myFixture.addFileToProject(path, content.replace("<caret>", "")).virtualFile
        myFixture.configureFromExistingVirtualFile(vf)
        myFixture.editor.caretModel.moveToOffset(caret)
        return ExtractContext.detect(myFixture.file, myFixture.editor)
    }

    private fun detectAt(content: String, fileName: String): ExtractTarget? {
        myFixture.configureByText(fileName, content)
        return ExtractContext.detect(myFixture.file, myFixture.editor)
    }
}
