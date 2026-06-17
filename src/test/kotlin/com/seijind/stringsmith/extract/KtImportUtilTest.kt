package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the shared import-decision logic that single and batch writers both rely on (#1 dedup). */
class KtImportUtilTest {

    @Test
    fun android_composableAndR() {
        val imps = KtImportUtil.resourceImportsFor(
            ResourceSystem.ANDROID,
            hasComposable = true,
            hasNonXmlReference = true,
            keys = listOf("hello"),
            androidRPackage = "com.example",
            cmpResPackage = null,
        )
        assertTrue(imps.contains("androidx.compose.ui.res.stringResource"))
        assertTrue(imps.contains("com.example.R"))
    }

    @Test
    fun android_xmlOnly_noRImport() {
        val imps = KtImportUtil.resourceImportsFor(
            ResourceSystem.ANDROID,
            hasComposable = false,
            hasNonXmlReference = false,
            keys = emptyList(),
            androidRPackage = "com.example",
            cmpResPackage = null,
        )
        assertTrue(imps.isEmpty())
    }

    @Test
    fun android_noRPackage_noRImport() {
        val imps = KtImportUtil.resourceImportsFor(
            ResourceSystem.ANDROID,
            hasComposable = false,
            hasNonXmlReference = true,
            keys = emptyList(),
            androidRPackage = null,
            cmpResPackage = null,
        )
        assertFalse(imps.any { it.endsWith(".R") })
    }

    @Test
    fun cmp_resKeysAndComposable_distinct() {
        val imps = KtImportUtil.resourceImportsFor(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            hasComposable = true,
            hasNonXmlReference = true,
            keys = listOf("hello", "hello", "bye"),
            androidRPackage = null,
            cmpResPackage = "com.example.res",
        )
        assertEquals(
            listOf(
                "com.example.res.Res",
                "com.example.res.hello",
                "com.example.res.bye",
                "org.jetbrains.compose.resources.stringResource",
            ),
            imps,
        )
    }

    @Test
    fun cmp_noResPackage_nothing() {
        val imps = KtImportUtil.resourceImportsFor(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            hasComposable = true,
            hasNonXmlReference = true,
            keys = listOf("hello"),
            androidRPackage = null,
            cmpResPackage = null,
        )
        assertTrue(imps.isEmpty())
    }
}
