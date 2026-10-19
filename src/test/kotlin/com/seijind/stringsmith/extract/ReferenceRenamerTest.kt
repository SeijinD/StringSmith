package com.seijind.stringsmith.extract

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReferenceRenamerTest : BasePlatformTestCase() {

    fun testRenamesOwnReferencesAndAliasesOnly() {
        val stringsXml = myFixture.addFileToProject(
            "res/values/strings.xml",
            """
            <resources>
                <string name="cancel">Cancel</string>
                <string name="dismiss">@string/cancel</string>
            </resources>
            """.trimIndent()
        ).virtualFile
        val kotlin = myFixture.addFileToProject(
            "src/Screen.kt",
            """
            fun show() {
                val own = R.string.cancel
                val platform = android.R.string.cancel
                log("R.string.cancel missing")
                // R.string.cancel in a comment
            }
            """.trimIndent()
        ).virtualFile

        val java = myFixture.addFileToProject(
            "src/Legacy.java",
            """
            class Legacy {
                int own = R.string.cancel;
                String text = "R.string.cancel missing";
            }
            """.trimIndent()
        ).virtualFile
        val layout = myFixture.addFileToProject(
            "res/layout/screen.xml",
            """
            <!-- was @string/cancel -->
            <TextView android:text="@string/cancel" />
            """.trimIndent()
        ).virtualFile

        val plan = ReferenceRenamer.planRename(project, stringsXml, "cancel", "abort", ResourceSystem.ANDROID)
        WriteCommandAction.runWriteCommandAction(project) {
            StringsXmlUtil.renameKey(stringsXml, "cancel", "abort")
            ReferenceRenamer.applyPlan(project, plan)
        }

        val docs = FileDocumentManager.getInstance()
        val xml = docs.getDocument(stringsXml)!!.text
        assertTrue(xml.contains("<string name=\"abort\">Cancel</string>"))
        assertTrue(xml.contains("<string name=\"dismiss\">@string/abort</string>"))
        val code = docs.getDocument(kotlin)!!.text
        assertTrue(code.contains("val own = R.string.abort"))
        assertTrue(code.contains("val platform = android.R.string.cancel"))
        assertTrue(code.contains("log(\"R.string.cancel missing\")"))
        assertTrue(code.contains("// R.string.cancel in a comment"))
        val javaCode = docs.getDocument(java)!!.text
        assertTrue(javaCode, javaCode.contains("int own = R.string.abort;"))
        assertTrue(javaCode, javaCode.contains("String text = \"R.string.cancel missing\";"))
        val layoutText = docs.getDocument(layout)!!.text
        assertTrue(layoutText, layoutText.contains("<!-- was @string/cancel -->"))
        assertTrue(layoutText, layoutText.contains("android:text=\"@string/abort\""))
    }
}
