package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyReferencesTest {

    private fun prefixIn(text: String, key: String): String? =
        KeyReferences.prefixAt(text, text.lastIndexOf(key), key, KeyReferences.ALL_PREFIXES)

    @Test
    fun matchesEachReferenceForm() {
        assertEquals("R.string.", prefixIn("getString(R.string.cancel)", "cancel"))
        assertEquals("Res.string.", prefixIn("stringResource(Res.string.cancel)", "cancel"))
        assertEquals("@string/", prefixIn("android:text=\"@string/cancel\"", "cancel"))
    }

    @Test
    fun matchesFullyQualifiedProjectR() {
        assertEquals("R.string.", prefixIn("com.example.app.R.string.cancel", "cancel"))
    }

    @Test
    fun matchesProjectRInPackageEndingWithAndroid() {
        assertEquals("R.string.", prefixIn("com.acme.android.R.string.cancel", "cancel"))
    }

    @Test
    fun rejectsPlatformAndroidR() {
        assertNull(prefixIn("android.R.string.cancel", "cancel"))
    }

    @Test
    fun rejectsLongerQualifierName() {
        assertNull(prefixIn("MyR.string.cancel", "cancel"))
        assertNull(prefixIn("MyRes.string.cancel", "cancel"))
    }

    @Test
    fun rejectsLongerKey() {
        assertNull(KeyReferences.prefixAt("R.string.cancel_all", 9, "cancel", KeyReferences.ALL_PREFIXES))
    }

    @Test
    fun rejectsKeyWithoutPrefix() {
        assertNull(prefixIn("<string name=\"cancel\">Cancel</string>", "cancel"))
    }
}
