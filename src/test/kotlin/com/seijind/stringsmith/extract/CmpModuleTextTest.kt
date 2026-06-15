package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CmpModuleTextTest {

    @Test
    fun parsePackageOfResClass_kotlinDslAssignment() {
        val gradle = """
            compose.resources {
                packageOfResClass = "com.example.app.generated.resources"
            }
        """.trimIndent()
        assertEquals("com.example.app.generated.resources", CmpModuleText.parsePackageOfResClass(gradle))
    }

    @Test
    fun parsePackageOfResClass_setCall() {
        val gradle = """compose.resources { packageOfResClass.set("com.x.y.res") }"""
        assertEquals("com.x.y.res", CmpModuleText.parsePackageOfResClass(gradle))
    }

    @Test
    fun parsePackageOfResClass_groovySingleQuote() {
        val gradle = "compose.resources { packageOfResClass = 'com.g.h.res' }"
        assertEquals("com.g.h.res", CmpModuleText.parsePackageOfResClass(gradle))
    }

    @Test
    fun parsePackageOfResClass_noneReturnsNull() {
        assertNull(CmpModuleText.parsePackageOfResClass("plugins { id(\"x\") }"))
    }

    @Test
    fun resolve_gradleOverridesEverything() {
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = """packageOfResClass = "com.declared.res"""",
            existingResImportPackages = listOf("com.scanned.generated.resources"),
            filePackage = "com.scanned.feature",
            moduleNamespace = "com.namespace"
        )
        assertEquals("com.declared.res", pkg)
    }

    @Test
    fun resolve_picksImportMatchingFilePackage() {
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = null,
            existingResImportPackages = listOf(
                "com.other.generated.resources",
                "com.example.app.generated.resources"
            ),
            filePackage = "com.example.app.ui",
            moduleNamespace = null
        )
        assertEquals("com.example.app.generated.resources", pkg)
    }

    @Test
    fun resolve_derivesFromFilePackageRatherThanBorrowingUnrelatedImport() {
        // No prefix match: prefer deriving from THIS file's package over an arbitrary other module's import.
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = null,
            existingResImportPackages = listOf("com.only.generated.resources"),
            filePackage = "org.unrelated.code",
            moduleNamespace = null
        )
        assertEquals("org.unrelated.code.generated.resources", pkg)
    }

    @Test
    fun resolve_fallsBackToFirstImportOnlyWhenNoModuleInfo() {
        // True last resort: no gradle, no prefix match, no namespace, no file package.
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = null,
            existingResImportPackages = listOf("com.only.generated.resources"),
            filePackage = "",
            moduleNamespace = null
        )
        assertEquals("com.only.generated.resources", pkg)
    }

    @Test
    fun resolve_derivesFromNamespaceWhenNoImports() {
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = "android { namespace = \"com.example.app\" }",
            existingResImportPackages = emptyList(),
            filePackage = "com.example.app.ui",
            moduleNamespace = "com.example.app"
        )
        assertEquals("com.example.app.generated.resources", pkg)
    }

    @Test
    fun resolve_derivesFromFilePackageWhenNoNamespace() {
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = null,
            existingResImportPackages = emptyList(),
            filePackage = "com.example.app.ui",
            moduleNamespace = null
        )
        assertEquals("com.example.app.ui.generated.resources", pkg)
    }

    @Test
    fun resolve_returnsNullWhenNothingAvailable() {
        val pkg = CmpModuleText.resolveResPackage(
            gradleText = null,
            existingResImportPackages = emptyList(),
            filePackage = "",
            moduleNamespace = null
        )
        assertNull(pkg)
    }
}
