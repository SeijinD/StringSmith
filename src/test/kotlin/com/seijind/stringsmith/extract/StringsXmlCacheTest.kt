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
}
