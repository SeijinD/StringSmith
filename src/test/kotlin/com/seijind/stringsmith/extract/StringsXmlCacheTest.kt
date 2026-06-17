package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StringsXmlCacheTest : BasePlatformTestCase() {

    /** readEntries caches per file; a later edit must invalidate it (keyed on the document mod stamp). */
    fun testReadEntriesReflectsEditsAfterCaching() {
        val file = myFixture.addFileToProject(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="a">A</string></resources>""",
        ).virtualFile

        assertEquals("A", StringsXmlUtil.findValueOfKey(file, "a")) // populate cache

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            doc.setText("""<resources><string name="a">B</string></resources>""")
        }

        assertEquals("B", StringsXmlUtil.findValueOfKey(file, "a")) // cache invalidated, not stale
    }

    /** readKeys returns declared keys in order and reflects edits (shares the stamp-keyed cache). */
    fun testReadKeysReflectsEdits() {
        val file = myFixture.addFileToProject(
            "readkeys/src/main/res/values/strings.xml",
            """<resources><string name="a">A</string><string name="b">B</string></resources>""",
        ).virtualFile

        assertEquals(listOf("a", "b"), StringsXmlUtil.readKeys(file).toList())

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            doc.setText("""<resources><string name="a">A</string><string name="c">C</string></resources>""")
        }

        assertEquals(listOf("a", "c"), StringsXmlUtil.readKeys(file).toList())
    }

    /** appendEntries writes every pair in one document edit; keyExists/findValueOfKey see them all. */
    fun testAppendEntriesWritesAllPairs() {
        val file = myFixture.addFileToProject(
            "appendentries/src/main/res/values/strings.xml",
            """<resources>
    <string name="a">A</string>
</resources>""",
        ).virtualFile

        WriteCommandAction.runWriteCommandAction(project) {
            StringsXmlUtil.appendEntries(file, listOf("b" to "B", "c" to "C"))
        }

        assertTrue(StringsXmlUtil.keyExists(file, "b"))
        assertEquals("C", StringsXmlUtil.findValueOfKey(file, "c"))
        assertEquals(listOf("a", "b", "c"), StringsXmlUtil.readKeys(file).toList())
    }
}
