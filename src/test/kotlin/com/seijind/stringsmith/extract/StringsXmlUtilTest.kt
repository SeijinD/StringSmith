package com.seijind.stringsmith.extract

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StringsXmlUtilTest : BasePlatformTestCase() {

    fun testDiscoversComposeResourcesDefault() {
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values/strings.xml",
            "<resources></resources>"
        )
        val all = StringsXmlUtil.findAllDefaultStringsXml(project)
        assertTrue(all.any { it.path.contains("composeResources/values/strings.xml") })
    }

    fun testDiscoversAndroidAndComposeTogether() {
        myFixture.addFileToProject("app/src/main/res/values/strings.xml", "<resources></resources>")
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values/strings.xml",
            "<resources></resources>"
        )
        val all = StringsXmlUtil.findAllDefaultStringsXml(project)
        assertTrue(all.any { ResourceSystem.of(it) == ResourceSystem.ANDROID })
        assertTrue(all.any { ResourceSystem.of(it) == ResourceSystem.COMPOSE_MULTIPLATFORM })
    }

    fun testFindsComposeLocaleVariants() {
        val default = myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values/strings.xml",
            "<resources></resources>"
        ).virtualFile
        myFixture.addFileToProject(
            "shared/src/commonMain/composeResources/values-de/strings.xml",
            "<resources></resources>"
        )
        val variants = StringsXmlUtil.findLocaleVariants(default)
        assertTrue(variants.any { it.path.contains("values-de") })
    }
}
