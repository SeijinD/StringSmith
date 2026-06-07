package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidModuleTextTest {

    @Test
    fun parseNamespaces_topLevel() {
        val gradle = """
            android {
                namespace = "com.example.app"
                compileSdk = 34
            }
        """.trimIndent()
        assertEquals(listOf("com.example.app"), AndroidModuleText.parseNamespaces(gradle))
    }

    @Test
    fun parseNamespaces_groovySyntax() {
        val gradle = """android { namespace 'com.example.app' }"""
        assertEquals(listOf("com.example.app"), AndroidModuleText.parseNamespaces(gradle))
    }

    @Test
    fun parseNamespaces_multipleFlavors() {
        val gradle = """
            productFlavors {
                create("google") {
                    namespace = "com.example.free"
                }
                create("huawei") {
                    namespace = "com.example.pro"
                }
            }
        """.trimIndent()
        assertEquals(listOf("com.example.free", "com.example.pro"), AndroidModuleText.parseNamespaces(gradle))
    }

    @Test
    fun parseNamespaces_distinctOnly() {
        val gradle = """
            namespace = "com.x"
            namespace = "com.x"
        """.trimIndent()
        assertEquals(listOf("com.x"), AndroidModuleText.parseNamespaces(gradle))
    }

    @Test
    fun parseNamespaces_emptyText() {
        assertEquals(emptyList<String>(), AndroidModuleText.parseNamespaces(""))
    }

    @Test
    fun parseManifestPackage_basic() {
        val manifest = """<manifest package="com.example.app" xmlns:android="..."/>"""
        assertEquals("com.example.app", AndroidModuleText.parseManifestPackage(manifest))
    }

    @Test
    fun parseManifestPackage_missing() {
        val manifest = """<manifest xmlns:android="..."/>"""
        assertNull(AndroidModuleText.parseManifestPackage(manifest))
    }

    @Test
    fun pickByFilePackagePrefix_exactMatch() {
        val pick = AndroidModuleText.pickByFilePackagePrefix(
            listOf("com.a", "com.b"),
            "com.a"
        )
        assertEquals("com.a", pick)
    }

    @Test
    fun pickByFilePackagePrefix_prefixMatch() {
        val pick = AndroidModuleText.pickByFilePackagePrefix(
            listOf("com.example.free", "com.example.pro"),
            "com.example.pro.feature.home"
        )
        assertEquals("com.example.pro", pick)
    }

    @Test
    fun pickByFilePackagePrefix_longestWins() {
        val pick = AndroidModuleText.pickByFilePackagePrefix(
            listOf("com", "com.app", "com.app.feature"),
            "com.app.feature.x"
        )
        assertEquals("com.app.feature", pick)
    }

    @Test
    fun pickByFilePackagePrefix_noMatch() {
        val pick = AndroidModuleText.pickByFilePackagePrefix(
            listOf("com.foo"),
            "com.bar.x"
        )
        assertNull(pick)
    }

    @Test
    fun pickByFilePackagePrefix_emptyFilePackage() {
        assertNull(AndroidModuleText.pickByFilePackagePrefix(listOf("com.a"), ""))
    }

    @Test
    fun longestCommonDottedPrefix_partial() {
        assertEquals(
            "com.example",
            AndroidModuleText.longestCommonDottedPrefix("com.example.free", "com.example.pro")
        )
    }

    @Test
    fun longestCommonDottedPrefix_fullMatch() {
        assertEquals(
            "com.foo.bar",
            AndroidModuleText.longestCommonDottedPrefix("com.foo.bar", "com.foo.bar")
        )
    }

    @Test
    fun longestCommonDottedPrefix_noOverlap() {
        assertEquals("", AndroidModuleText.longestCommonDottedPrefix("com.a", "org.b"))
    }

    @Test
    fun deriveFromSourceLayout_javaSrcSet() {
        val pkg = AndroidModuleText.deriveFromSourceLayout(
            filePath = "C:/proj/app/src/main/java/com/example/pro/feature/Home.kt",
            moduleRootPath = "C:/proj/app",
            filePackage = "com.example.pro.feature"
        )
        assertEquals("com.example.pro.feature", pkg)
    }

    @Test
    fun deriveFromSourceLayout_kotlinSrcSet() {
        val pkg = AndroidModuleText.deriveFromSourceLayout(
            filePath = "/proj/app/src/main/kotlin/com/example/feature/X.kt",
            moduleRootPath = "/proj/app",
            filePackage = "com.example.feature"
        )
        assertEquals("com.example.feature", pkg)
    }

    @Test
    fun deriveFromSourceLayout_windowsPaths() {
        val pkg = AndroidModuleText.deriveFromSourceLayout(
            filePath = "C:\\proj\\app\\src\\main\\java\\com\\example\\X.kt",
            moduleRootPath = "C:\\proj\\app",
            filePackage = "com.example"
        )
        assertEquals("com.example", pkg)
    }

    @Test
    fun deriveFromSourceLayout_fileOutsideModule() {
        val pkg = AndroidModuleText.deriveFromSourceLayout(
            filePath = "/other/X.kt",
            moduleRootPath = "/proj/app",
            filePackage = "com.x"
        )
        assertNull(pkg)
    }

    @Test
    fun resolveRPackage_multiFlavorPicksByFilePackage() {
        val gradle = """
            create("google") { namespace = "com.example.free" }
            create("huawei") { namespace = "com.example.pro" }
        """.trimIndent()
        val result = AndroidModuleText.resolveRPackage(
            gradleText = gradle,
            manifestText = null,
            filePath = "/proj/app/src/huawei/java/com/example/pro/X.kt",
            moduleRootPath = "/proj/app",
            filePackage = "com.example.pro.feature"
        )
        assertEquals("com.example.pro", result)
    }

    @Test
    fun resolveRPackage_fallsBackToFirstNamespace() {
        val gradle = """namespace = "com.example.app""""
        val result = AndroidModuleText.resolveRPackage(
            gradleText = gradle,
            manifestText = null,
            filePath = null,
            moduleRootPath = null,
            filePackage = "unrelated.pkg"
        )
        assertEquals("com.example.app", result)
    }

    @Test
    fun resolveRPackage_usesManifestWhenNoGradle() {
        val manifest = """<manifest package="com.legacy.app"/>"""
        val result = AndroidModuleText.resolveRPackage(
            gradleText = null,
            manifestText = manifest,
            filePath = null,
            moduleRootPath = null,
            filePackage = "com.legacy.app.feature"
        )
        assertEquals("com.legacy.app", result)
    }

    @Test
    fun resolveRPackage_nothingFound() {
        val result = AndroidModuleText.resolveRPackage(
            gradleText = null,
            manifestText = null,
            filePath = null,
            moduleRootPath = null,
            filePackage = "com.x"
        )
        assertNull(result)
    }
}
