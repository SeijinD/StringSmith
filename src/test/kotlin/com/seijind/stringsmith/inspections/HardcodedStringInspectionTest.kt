package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.seijind.stringsmith.StringSmithBundle

class HardcodedStringInspectionTest : BasePlatformTestCase() {

    private val uiMessage = StringSmithBundle.message("inspection.hardcodedString.message")
    private val nonUiMessage = StringSmithBundle.message("inspection.hardcodedNonUiString.message")

    /** Literal texts (with quotes) reported by [inspection] in a file added at [path]. */
    private fun reported(inspection: LocalInspectionTool, message: String, path: String, content: String): List<String> {
        val vf = myFixture.addFileToProject(path, content).virtualFile
        myFixture.configureFromExistingVirtualFile(vf)
        myFixture.enableInspections(inspection)
        val text = myFixture.file.text
        return myFixture.doHighlighting()
            .filter { it.description == message }
            .map { text.substring(it.startOffset, it.endOffset) }
    }

    private fun ui(path: String, content: String) = reported(HardcodedUiStringInspection(), uiMessage, path, content)

    private fun nonUi(path: String, content: String) = reported(HardcodedNonUiStringInspection(), nonUiMessage, path, content)

    private fun composable(body: String): String = """
        import androidx.compose.runtime.Composable

        @Composable
        fun Screen(name: String, count: Int, id: String) {
            $body
        }
    """.trimIndent()

    // ---- Compose: UI text is reported ----

    fun testComposableTextIsUi() {
        assertEquals(listOf("\"Hello\""), ui("src/Screen.kt", composable("""Text("Hello")""")))
    }

    fun testComposableNestedLayoutTextIsUi() {
        assertEquals(listOf("\"Save\""), ui("src/Screen.kt", composable("""Column { Button(onClick = {}) { Text("Save") } }""")))
    }

    fun testComposableLabelLambdaIsUi() {
        assertEquals(listOf("\"Name\""), ui("src/Screen.kt", composable("""TextField(value = name, onValueChange = {}, label = { Text("Name") })""")))
    }

    fun testComposableContentDescriptionIsUi() {
        assertEquals(listOf("\"Close\""), ui("src/Screen.kt", composable("""Icon(Icons.Default.Close, contentDescription = "Close")""")))
    }

    fun testComposableCustomComponentArgumentsAreUi() {
        val found = ui("src/Screen.kt", composable("""MyCard(title = "Welcome"); MyCard("Goodbye")"""))
        assertEquals(listOf("\"Welcome\"", "\"Goodbye\""), found)
    }

    fun testComposableTemplateWithTextIsUi() {
        assertEquals(listOf("\"Hello ${'$'}name\""), ui("src/Screen.kt", composable("""Text("Hello ${'$'}name")""")))
    }

    fun testComposableInCommonMainIsUi() {
        assertEquals(listOf("\"Hello\""), ui("composeApp/src/commonMain/kotlin/Screen.kt", composable("""Text("Hello")""")))
    }

    // ---- Compose: technical values are not UI ----

    fun testComposableTestTagIsNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""Box(Modifier.testTag("login_button"))""")))
    }

    fun testComposableNavigationRouteIsNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""navController.navigate("details")""")))
    }

    fun testComposableKeysAndEffectsAreNotUi() {
        val found = ui(
            "src/Screen.kt",
            composable("""key("item") {}; val s = remember("state") { 0 }; LaunchedEffect("load") {}""")
        )
        assertEmpty(found)
    }

    fun testComposableResourcePathsAreNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""Image(painterResource("drawable/logo.xml"), null); Font("fonts/inter.ttf")""")))
    }

    fun testComposableNamedRouteArgumentIsNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""Destination(route = "home")""")))
    }

    fun testComposableComparisonsAreNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""if (id == "home") Text(id); when (id) { "settings" -> Unit }""")))
    }

    fun testComposableTechnicalStringsAreReportedByNonUi() {
        val found = nonUi("src/Screen.kt", composable("""Box(Modifier.testTag("login_button")); Text("Hello")"""))
        assertEquals(listOf("\"login_button\""), found)
    }

    // ---- Never reported ----

    fun testStringsWithoutLettersAreIgnored() {
        val body = """Text("--"); Text("100"); Text(" • "); Text("${'$'}count")"""
        assertEmpty(ui("src/Screen.kt", composable(body)))
        assertEmpty(nonUi("src/Other.kt", composable(body)))
    }

    fun testPreviewComposablesAreIgnored() {
        val content = """
            import androidx.compose.runtime.Composable

            @Preview
            @Composable
            fun A() { Text("Preview one") }

            @PreviewLightDark
            @Composable
            fun B() { Text("Preview two") }

            @DevicePreviews
            @Composable
            fun C() { Text("Preview three") }
        """.trimIndent()
        assertEmpty(ui("src/Previews.kt", content))
    }

    fun testPreviewParameterProviderIsIgnored() {
        val content = """
            class NamesProvider : PreviewParameterProvider<String> {
                override val values = sequenceOf("Alice", "Bob")
            }
        """.trimIndent()
        assertEmpty(ui("src/Provider.kt", content))
        assertEmpty(nonUi("src/Provider2.kt", content))
    }

    fun testConstAnnotationsExceptionsAndLoggingAreIgnored() {
        val content = """
            const val ROUTE_HOME = "home_route"

            @SerialName("user_name")
            class User

            fun work(value: String?) {
                Log.d("Tag", "Loaded user")
                Timber.i("Loaded user")
                Napier.d("Loaded user")
                logger.info("Loaded user")
                println("Loaded user")
                require(value != null) { "Value missing" }
                error("Broken state")
                TODO("Not implemented")
                throw IllegalStateException("Invalid user")
            }
        """.trimIndent()
        assertEmpty(nonUi("src/Work.kt", content))
    }

    // ---- Outside Compose: UI sinks only ----

    fun testToastAndSnackbarAreUi() {
        val content = """
            fun show(context: Context, view: View) {
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                Snackbar.make(view, "Deleted", Snackbar.LENGTH_LONG).setAction("Undo") {}.show()
            }
        """.trimIndent()
        assertEquals(listOf("\"Saved\"", "\"Deleted\"", "\"Undo\""), ui("src/Show.kt", content))
    }

    fun testViewSettersAndPropertiesAreUi() {
        val content = """
            fun bind(textView: TextView, builder: AlertDialog.Builder) {
                textView.text = "Hello there"
                textView.hint = "Your name"
                builder.setTitle("Delete item?")
                builder.setPositiveButton("Delete") { _, _ -> }
            }
        """.trimIndent()
        assertEquals(listOf("\"Hello there\"", "\"Your name\"", "\"Delete item?\"", "\"Delete\""), ui("src/Bind.kt", content))
    }

    fun testShowSnackbarInViewModelIsUi() {
        val content = """
            class ProfileViewModel(private val host: SnackbarHostState) {
                suspend fun saved() { host.showSnackbar("Profile saved") }
            }
        """.trimIndent()
        assertEquals(listOf("\"Profile saved\""), ui("src/ProfileViewModel.kt", content))
    }

    fun testNotificationTextIsUi() {
        val content = """
            fun notify(builder: NotificationCompat.Builder) {
                builder.setContentTitle("Download finished").setContentText("Tap to open")
            }
        """.trimIndent()
        assertEquals(listOf("\"Download finished\"", "\"Tap to open\""), ui("src/Notify.kt", content))
    }

    fun testActivityKeysAreNotUiButAreNonUi() {
        val content = """
            class MainActivity : AppCompatActivity() {
                fun open() {
                    val intent = Intent(this, Detail::class.java)
                    intent.putExtra("user_id", 1)
                    getSharedPreferences("settings", 0)
                    val label = "Welcome back"
                }
            }
        """.trimIndent()
        assertEmpty(ui("src/MainActivity.kt", content))
        assertEquals(listOf("\"user_id\"", "\"settings\"", "\"Welcome back\""), nonUi("src/MainActivity2.kt", content))
    }

    fun testPlainKotlinIsOnlyNonUi() {
        val content = """
            class Repository {
                val endpoint = "users/profile"
                fun header() = mapOf("Accept" to "json")
            }
        """.trimIndent()
        assertEmpty(ui("src/Repository.kt", content))
        assertEquals(listOf("\"users/profile\"", "\"Accept\"", "\"json\""), nonUi("src/Repository2.kt", content))
    }

    // ---- Excluded files ----

    // The platform itself refuses to highlight Gradle scripts in the fixture, so check the rule directly.
    fun testGradleScriptIsExcluded() {
        val script = myFixture.addFileToProject("app/build.gradle.kts", """android { namespace = "com.example.app" }""")
        assertTrue(HardcodedStringRules.isExcludedFile(script))
        val source = myFixture.addFileToProject("app/src/main/kotlin/Screen.kt", "fun f() = Unit")
        assertFalse(HardcodedStringRules.isExcludedFile(source))
    }

    fun testLazyLoggingMessageIsIgnored() {
        assertEmpty(nonUi("src/Lazy.kt", """fun f() { logger.debug { "Loaded user" } }"""))
    }

    fun testComposableNamedLikeErrorStillShowsUi() {
        assertEquals(listOf("\"Something went wrong\""), ui("src/Screen.kt", composable("""ShowError { Text("Something went wrong") }""")))
    }

    fun testComposableEventHandlerIsNotUi() {
        assertEmpty(ui("src/Screen.kt", composable("""Button(onClick = { analytics.track("save_clicked") }) { }""")))
    }

    fun testComposableSlotLambdasAreUi() {
        val body = """AlertDialog(onDismissRequest = {}, title = { Text("Delete?") }, confirmButton = { TextButton(onClick = {}) { Text("Delete") } })"""
        assertEquals(listOf("\"Delete?\"", "\"Delete\""), ui("src/Screen.kt", composable(body)))
    }

    fun testTestSourceSetsAreExcluded() {
        val content = composable("""Text("Hello")""")
        assertEmpty(ui("app/src/test/kotlin/ScreenTest.kt", content))
        assertEmpty(ui("app/src/androidTest/kotlin/ScreenTest.kt", content))
        assertEmpty(ui("shared/src/commonTest/kotlin/ScreenTest.kt", content))
        assertEmpty(ui("app/src/androidUnitTest/kotlin/ScreenTest.kt", content))
        assertEmpty(ui("app/src/androidInstrumentedTest/kotlin/ScreenTest.kt", content))
        assertEmpty(ui("app/src/testDebug/kotlin/ScreenTest.kt", content))
    }

    fun testSourceSetMerelyContainingTestLettersIsNotExcluded() {
        assertEquals(listOf("\"Hello\""), ui("app/src/latest/kotlin/Screen.kt", composable("""Text("Hello")""")))
    }

    fun testGeneratedAndBuildLogicAreExcluded() {
        val content = composable("""Text("Hello")""")
        assertEmpty(ui("app/build/generated/ksp/Screen.kt", content))
        assertEmpty(ui("buildSrc/src/main/kotlin/Screen.kt", content))
        assertEmpty(ui("build-logic/convention/src/main/kotlin/Screen.kt", content))
    }

    fun testBaselineProfileAndBenchmarkModulesAreExcluded() {
        myFixture.addFileToProject("baselineprofile/build.gradle.kts", """plugins { id("com.android.test") }""")
        assertEmpty(nonUi("baselineprofile/src/main/kotlin/Generator.kt", """val packageName = "com.example.app""""))
        myFixture.addFileToProject("benchmark/build.gradle.kts", """plugins { alias(libs.plugins.androidx.baselineprofile) }""")
        assertEmpty(nonUi("benchmark/src/main/kotlin/Startup.kt", """val packageName = "com.example.app""""))
    }

    // ---- XML ----

    fun testLayoutTextAttributesAreUi() {
        val content = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:text="Welcome" android:hint="Your name" android:layout_width="match_parent" />
                <TextView android:text="@string/existing" />
            </LinearLayout>
        """.trimIndent()
        assertEquals(listOf("\"Welcome\"", "\"Your name\""), ui("res/layout/screen.xml", content))
    }

    fun testLayoutXmlIsNotReportedByNonUi() {
        val content = """<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:text="Welcome" />"""
        assertEmpty(nonUi("res/layout/screen.xml", content))
    }

    fun testManifestLabelIsUi() {
        val content = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="My App" android:name=".App">
                    <activity android:name=".Main" android:label="Home" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()
        assertEquals(listOf("\"My App\"", "\"Home\""), ui("app/src/main/AndroidManifest.xml", content))
    }

    fun testShortcutLabelsAreUi() {
        val content = """
            <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
                <shortcut android:shortcutId="compose" android:shortcutShortLabel="Compose" android:shortcutLongLabel="Compose message" />
            </shortcuts>
        """.trimIndent()
        assertEquals(listOf("\"Compose\"", "\"Compose message\""), ui("res/xml/shortcuts.xml", content))
    }

    fun testValuesXmlIsNotReported() {
        val content = """<resources><string name="app_name">My App</string><style name="Theme" parent="Theme.Material3" /></resources>"""
        assertEmpty(ui("res/values/strings.xml", content))
    }

    fun testGeneratedManifestUnderBuildIsExcluded() {
        val content = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="My App" /></manifest>"""
        assertEmpty(ui("app/build/intermediates/merged_manifest/AndroidManifest.xml", content))
    }
}
