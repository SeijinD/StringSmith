package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.seijind.stringsmith.settings.StringSmithSettings

class ModuleResolverTest : BasePlatformTestCase() {

    fun testInsideModule_androidLayout() {
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val strings = myFixture.addFileToProject("app/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        val file = myFixture.addFileToProject("app/src/main/kotlin/com/x/Foo.kt", "package com.x")
        assertTrue(ModuleResolver.isFileInsideStringsXmlModule(file, strings))
    }

    fun testInsideModule_composeMultiplatformLayout() {
        myFixture.addFileToProject("shared/build.gradle.kts", "")
        val strings = myFixture.addFileToProject("shared/src/commonMain/composeResources/values/strings.xml", EMPTY_RES).virtualFile
        val file = myFixture.addFileToProject("shared/src/commonMain/kotlin/com/x/Foo.kt", "package com.x")
        assertTrue(ModuleResolver.isFileInsideStringsXmlModule(file, strings))
    }

    fun testNotInsideModule_differentModule() {
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val strings = myFixture.addFileToProject("app/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        myFixture.addFileToProject("other/build.gradle.kts", "")
        val file = myFixture.addFileToProject("other/src/main/kotlin/com/y/Bar.kt", "package com.y")
        assertFalse(ModuleResolver.isFileInsideStringsXmlModule(file, strings))
    }

    fun testChooseInitialTarget_picksNearestModule() {
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val appStrings = myFixture.addFileToProject("app/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        myFixture.addFileToProject("lib/build.gradle.kts", "")
        val libStrings = myFixture.addFileToProject("lib/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        val file = myFixture.addFileToProject("lib/src/main/kotlin/com/y/Bar.kt", "package com.y")
        val chosen = ModuleResolver.chooseInitialTarget(project, file, listOf(appStrings, libStrings), StringSmithSettings())
        assertEquals(libStrings.path, chosen.path)
    }

    fun testChooseInitialTarget_remembersLastModuleWhenNotInside() {
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val appStrings = myFixture.addFileToProject("app/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        myFixture.addFileToProject("lib/build.gradle.kts", "")
        val libStrings = myFixture.addFileToProject("lib/src/main/res/values/strings.xml", EMPTY_RES).virtualFile
        val file = myFixture.addFileToProject("standalone/Foo.kt", "")
        val settings = StringSmithSettings().apply { rememberLastModule = true; lastTargetModulePath = libStrings.path }
        val chosen = ModuleResolver.chooseInitialTarget(project, file, listOf(appStrings, libStrings), settings)
        assertEquals(libStrings.path, chosen.path)
    }

    private companion object {
        const val EMPTY_RES = "<resources></resources>"
    }
}
