package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditDuplicateWriterTest : BasePlatformTestCase() {

    private fun detectXml(content: String): Pair<DuplicateSource, com.intellij.openapi.vfs.VirtualFile> {
        val xml = myFixture.addFileToProject("app/src/main/res/values/strings.xml", content)
        myFixture.configureFromExistingVirtualFile(xml.virtualFile)
        return DuplicateContext.detect(project, myFixture.file, myFixture.editor)!! to xml.virtualFile
    }

    private fun textOf(file: com.intellij.openapi.vfs.VirtualFile): String =
        FileDocumentManager.getInstance().getDocument(file)!!.text

    fun testDuplicateCopiesMarkupAndEntitiesVerbatim() {
        val (source, file) = detectXml(
            """<resources><string name="wel<caret>come"><b>Hi</b> &amp; &#169; \"x\"</string></resources>"""
        )
        DuplicateWriter.write(project, source, DuplicateDialogResult(newKey = "welcome_copy", updateReference = false))
        assertTrue(textOf(file).contains("""<string name="welcome_copy"><b>Hi</b> &amp; &#169; \"x\"</string>"""))
    }

    fun testEditRenameKeepsUnchangedMarkupValue() {
        val (source, file) = detectXml(
            """<resources><string name="wel<caret>come"><b>Hi</b> &#169;</string></resources>"""
        )
        EditWriter.write(
            project,
            source,
            EditDialogResult(
                originalKey = "welcome",
                newKey = "greeting",
                newDefaultValue = source.defaultValue,
                localeEdits = emptyList()
            )
        )
        assertTrue(textOf(file).contains("""<string name="greeting"><b>Hi</b> &#169;</string>"""))
    }
}
