package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModuleRootUtilTest : BasePlatformTestCase() {

    fun testFindsModuleRootByBuildGradleKts() {
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val src = myFixture.addFileToProject("app/src/main/kotlin/com/x/Foo.kt", "package com.x").virtualFile
        assertEquals("app", ModuleRootUtil.findModuleRoot(src)?.name)
    }

    fun testFindsModuleRootByBuildGradleGroovy() {
        myFixture.addFileToProject("legacy/build.gradle", "")
        val src = myFixture.addFileToProject("legacy/src/main/kotlin/Bar.kt", "").virtualFile
        assertEquals("legacy", ModuleRootUtil.findModuleRoot(src)?.name)
    }

    fun testFindsModuleRootByAndroidManifest() {
        myFixture.addFileToProject("feature/src/main/AndroidManifest.xml", "<manifest/>")
        val src = myFixture.addFileToProject("feature/src/main/kotlin/Baz.kt", "").virtualFile
        assertEquals("feature", ModuleRootUtil.findModuleRoot(src)?.name)
    }

    fun testPicksNearestModuleRootWhenNested() {
        // Both the outer project and the inner module carry a gradle file; the nearest must win.
        myFixture.addFileToProject("build.gradle.kts", "")
        myFixture.addFileToProject("app/build.gradle.kts", "")
        val src = myFixture.addFileToProject("app/src/main/kotlin/Foo.kt", "").virtualFile
        assertEquals("app", ModuleRootUtil.findModuleRoot(src)?.name)
    }

    fun testReturnsNullWhenNoModuleMarker() {
        val src = myFixture.addFileToProject("loose/nested/Foo.kt", "").virtualFile
        assertNull(ModuleRootUtil.findModuleRoot(src))
    }

    fun testReadGradleTextReturnsContent() {
        myFixture.addFileToProject("app/build.gradle.kts", "android { namespace = \"com.x\" }")
        val src = myFixture.addFileToProject("app/src/main/kotlin/Foo.kt", "").virtualFile
        val root = ModuleRootUtil.findModuleRoot(src)!!
        assertTrue(ModuleRootUtil.readGradleText(root)?.contains("namespace") == true)
    }
}
