package com.seijind.stringsmith.extract

import com.seijind.stringsmith.extract.BatchStatus.RowFacts
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the batch status precedence (Invalid > Reuse > Collision > Duplicate > New), pure — no IDE fixture. */
class BatchStatusTest {

    private fun row(key: String, value: String, include: Boolean = true) = RowFacts(include, key, value)

    private fun status(
        rows: List<RowFacts>,
        selfIndex: Int = 0,
        existingKey: String? = null,
        keyExistsInXml: Boolean = false,
    ): BatchRowStatus = BatchStatus.compute(
        rows, selfIndex, rows[selfIndex].key, rows[selfIndex].value, existingKey, keyExistsInXml
    )

    @Test
    fun new_whenUniqueAndValid() {
        assertEquals(BatchRowStatus.NEW, status(listOf(row("greeting", "Hello"))))
    }

    @Test
    fun invalid_whenKeyMalformed() {
        assertEquals(BatchRowStatus.INVALID, status(listOf(row("1bad", "Hello"))))
        assertEquals(BatchRowStatus.INVALID, status(listOf(row("bad-key", "Hello"))))
        assertEquals(BatchRowStatus.INVALID, status(listOf(row("", "Hello"))))
    }

    @Test
    fun reuse_whenKeyMatchesExisting() {
        assertEquals(
            BatchRowStatus.REUSE,
            status(listOf(row("greeting", "Hello")), existingKey = "greeting")
        )
    }

    @Test
    fun collision_whenKeyAlreadyInXml() {
        assertEquals(
            BatchRowStatus.COLLISION,
            status(listOf(row("greeting", "Hello")), keyExistsInXml = true)
        )
    }

    @Test
    fun collision_whenOtherIncludedRowReusesKeyWithDifferentValue() {
        val rows = listOf(row("greeting", "Hello"), row("greeting", "Hi"))
        assertEquals(BatchRowStatus.COLLISION, status(rows, selfIndex = 0))
    }

    @Test
    fun noCollision_whenColludingRowIsExcluded() {
        val rows = listOf(row("greeting", "Hello"), row("greeting", "Hi", include = false))
        assertEquals(BatchRowStatus.NEW, status(rows, selfIndex = 0))
    }

    @Test
    fun duplicate_whenOtherRowCarriesSameValue() {
        val rows = listOf(row("greeting", "Hello"), row("salutation", "Hello"))
        assertEquals(BatchRowStatus.DUPLICATE, status(rows, selfIndex = 0))
    }

    // --- Precedence ordering ---

    @Test
    fun invalidBeatsReuse() {
        assertEquals(
            BatchRowStatus.INVALID,
            status(listOf(row("bad-key", "Hello")), existingKey = "bad-key")
        )
    }

    @Test
    fun reuseBeatsCollision() {
        // Key both matches its existing key AND is present in xml — reuse wins (it's the same entry).
        assertEquals(
            BatchRowStatus.REUSE,
            status(listOf(row("greeting", "Hello")), existingKey = "greeting", keyExistsInXml = true)
        )
    }

    @Test
    fun collisionBeatsDuplicate() {
        // Same value as another row (duplicate) but key also collides in xml — collision wins.
        val rows = listOf(row("greeting", "Hello"), row("salutation", "Hello"))
        assertEquals(BatchRowStatus.COLLISION, status(rows, selfIndex = 0, keyExistsInXml = true))
    }
}
