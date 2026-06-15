package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.psi.KtFile

class CmpModuleUtilTest : BasePlatformTestCase() {

    fun testOverrideWinsOverEverything() {
        val settings = StringSmithSettings().apply { cmpResPackageOverride = "com.declared.res" }
        val ktFile = myFixture.addFileToProject(
            "src/commonMain/kotlin/com/example/app/ui/Screen.kt",
            """
            package com.example.app.ui
            import com.example.app.generated.resources.Res
            fun f() { val x = Res.string.welcome }
            """.trimIndent()
        ) as KtFile
        val pkg = CmpModuleUtil.findResPackage(project, ktFile.virtualFile, ktFile, settings)
        assertEquals("com.declared.res", pkg)
    }

    fun testScanFindsExistingResImport() {
        val ktFile = myFixture.addFileToProject(
            "src/commonMain/kotlin/com/example/app/ui/Screen.kt",
            """
            package com.example.app.ui
            import com.example.app.generated.resources.Res
            import com.example.app.generated.resources.welcome
            fun f() { val x = Res.string.welcome }
            """.trimIndent()
        ) as KtFile
        val pkg = CmpModuleUtil.findResPackage(project, ktFile.virtualFile, ktFile, StringSmithSettings())
        assertEquals("com.example.app.generated.resources", pkg)
    }

    fun testDerivesFromFilePackageWhenNothingElse() {
        val ktFile = myFixture.addFileToProject(
            "src/commonMain/kotlin/com/example/app/ui/Empty.kt",
            """
            package com.example.app.ui
            fun f() = 1
            """.trimIndent()
        ) as KtFile
        val pkg = CmpModuleUtil.findResPackage(project, ktFile.virtualFile, ktFile, StringSmithSettings())
        assertEquals("com.example.app.ui.generated.resources", pkg)
    }
}
