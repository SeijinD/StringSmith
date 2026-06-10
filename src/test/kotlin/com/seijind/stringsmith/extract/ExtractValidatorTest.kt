package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.seijind.stringsmith.settings.StringSmithSettings

class ExtractValidatorTest : BasePlatformTestCase() {

    private fun targetAt(content: String, fileName: String = "Foo.kt"): ExtractTarget {
        myFixture.configureByText(fileName, content)
        return ExtractContext.detect(myFixture.file, myFixture.editor)!!
    }

    fun testValidate_shortStringRejected() {
        val settings = StringSmithSettings().apply { minStringLength = 5 }
        val target = targetAt("fun foo() { val x = \"ab<caret>\" }")
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Rejected)
        assertTrue((r as ExtractValidator.Result.Rejected).reason.contains("5"))
    }

    fun testValidate_exclusionPatternRejected() {
        val settings = StringSmithSettings().apply {
            minStringLength = 1
            excludePatterns = "^[A-Z_]{2,}\$"
        }
        val target = targetAt("fun foo() { val x = \"DEBUG<caret>\" }")
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Rejected)
        assertTrue((r as ExtractValidator.Result.Rejected).reason.contains("exclusion"))
    }

    fun testValidate_urlExclusion() {
        val settings = StringSmithSettings()
        val target = targetAt("fun foo() { val x = \"https://google.com<caret>\" }")
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Rejected)
    }

    fun testValidate_previewComposableRejected() {
        val settings = StringSmithSettings().apply { excludePreviewComposables = true }
        val target = targetAt(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            @Preview
            @Composable
            fun GreetingPreview() {
                val x = "Hello<caret> world"
            }
            """.trimIndent(),
            "GreetingPreview.kt"
        )
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Rejected)
        assertTrue((r as ExtractValidator.Result.Rejected).reason.contains("Preview"))
    }

    fun testValidate_previewComposableAcceptedWhenDisabled() {
        val settings = StringSmithSettings().apply { excludePreviewComposables = false }
        val target = targetAt(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            @Preview
            @Composable
            fun GreetingPreview() {
                val x = "Hello<caret> world"
            }
            """.trimIndent(),
            "GreetingPreview.kt"
        )
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Ok)
    }

    fun testValidate_trimsWhitespace() {
        val settings = StringSmithSettings().apply { trimWhitespace = true; minStringLength = 1 }
        val target = targetAt("fun foo() { val x = \"  Hello  <caret>\" }")
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Ok)
        assertEquals("Hello", (r as ExtractValidator.Result.Ok).target.rawValue)
    }

    fun testValidate_okPath() {
        val settings = StringSmithSettings()
        val target = targetAt("fun foo() { val x = \"Welcome<caret>\" }")
        val r = ExtractValidator.validate(target, settings)
        assertTrue(r is ExtractValidator.Result.Ok)
    }
}
