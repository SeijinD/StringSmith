package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.seijind.stringsmith.settings.StringSmithSettings

class BatchScannerTest : BasePlatformTestCase() {

    fun testScanFindsAllKotlinLiterals() {
        val file = myFixture.configureByText(
            "Foo.kt",
            """
            import androidx.compose.runtime.Composable
            @Composable
            fun Greet() {
                val a = "Welcome"
                val b = "Goodbye"
                val c = "Hello"
            }
            """.trimIndent()
        )
        val results = BatchScanner.scan(file, StringSmithSettings())
        val values = results.map { it.rawValue }.toSet()
        assertTrue(values.containsAll(setOf("Welcome", "Goodbye", "Hello")))
    }

    fun testScanSkipsShortStrings() {
        val file = myFixture.configureByText(
            "Foo.kt",
            """
            object Constants {
                val short = "ok"
                val ok = "Welcome"
            }
            """.trimIndent()
        )
        val settings = StringSmithSettings().apply { minStringLength = 5 }
        val results = BatchScanner.scan(file, settings)
        val values = results.map { it.rawValue }
        assertFalse(values.contains("ok"))
        assertTrue(values.contains("Welcome"))
    }

    fun testScanSkipsExclusionMatches() {
        val file = myFixture.configureByText(
            "Foo.kt",
            """
            object Constants {
                val tag = "DEBUG_TAG"
                val ok = "Welcome"
            }
            """.trimIndent()
        )
        val settings = StringSmithSettings().apply { excludePatterns = "^[A-Z_]{2,}\$" }
        val results = BatchScanner.scan(file, settings)
        val values = results.map { it.rawValue }
        assertFalse(values.contains("DEBUG_TAG"))
        assertTrue(values.contains("Welcome"))
    }

    fun testScanSkipsPreviewComposables() {
        val file = myFixture.configureByText(
            "Foo.kt",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            @Composable
            fun Real() { val a = "RealString" }
            @Preview
            @Composable
            fun MyPreview() { val b = "PreviewDummy" }
            """.trimIndent()
        )
        val settings = StringSmithSettings().apply { excludePreviewComposables = true }
        val results = BatchScanner.scan(file, settings)
        val values = results.map { it.rawValue }
        assertTrue(values.contains("RealString"))
        assertFalse(values.contains("PreviewDummy"))
    }

    fun testScanReturnsEmptyForFileWithoutLiterals() {
        val file = myFixture.configureByText(
            "Empty.kt",
            """
            class Empty { fun foo() = 42 }
            """.trimIndent()
        )
        val results = BatchScanner.scan(file, StringSmithSettings())
        assertTrue(results.isEmpty())
    }
}
