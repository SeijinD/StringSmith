package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile

class EnsureImportTest : BasePlatformTestCase() {

    private fun importInto(content: String, fqName: String): String {
        val file = myFixture.configureByText("Screen.kt", content) as KtFile
        WriteCommandAction.runWriteCommandAction(project) { KtImportUtil.ensureImport(file, fqName) }
        return file.text
    }

    fun testInsertsInSortedPositionAndKeepsUnusedImports() {
        val text = importInto("package p\n\nimport a.Unused\nimport c.D\n\nfun f() {}\n", "b.X")
        assertEquals("package p\n\nimport a.Unused\nimport b.X\nimport c.D\n\nfun f() {}\n", text)
    }

    fun testAppendsAfterLastImport() {
        val text = importInto("package p\n\nimport a.B\n\nfun f() {}\n", "z.Y")
        assertEquals("package p\n\nimport a.B\nimport z.Y\n\nfun f() {}\n", text)
    }

    fun testAddsFirstImport() {
        val text = importInto("package p\n\nfun f() {}\n", "z.Y")
        assertTrue(text, text.contains("import z.Y\n"))
        assertTrue(text, text.contains("fun f() {}"))
        assertFalse(text, text.contains("import z.Yfun"))
    }

    fun testSkipsExistingImport() {
        val text = importInto("package p\n\nimport a.B\n\nfun f() {}\n", "a.B")
        assertEquals("package p\n\nimport a.B\n\nfun f() {}\n", text)
    }
}
