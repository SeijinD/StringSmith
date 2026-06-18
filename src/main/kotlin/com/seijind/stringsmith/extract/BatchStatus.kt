package com.seijind.stringsmith.extract

/**
 * Pure batch-row status decision, split out of [BatchDialog] so the precedence rules can be unit-tested
 * without an IDE fixture. Works on [RowFacts] (the only fields the rules read) so it never touches the
 * PSI-bearing [BatchRow]; whether the key already exists in the target `strings.xml` is passed in as
 * [keyExistsInXml] rather than read here.
 */
object BatchStatus {

    /** The row fields the precedence rules read — decoupled from PSI so tests can build rows directly. */
    data class RowFacts(val include: Boolean, val key: String, val value: String)

    /** Status precedence: Invalid > Reuse > Collision > Duplicate > New. */
    fun compute(
        rows: List<RowFacts>,
        selfIndex: Int,
        key: String,
        value: String,
        existingKey: String?,
        keyExistsInXml: Boolean
    ): BatchRowStatus {
        if (!KeyGenerator.isValidKey(key)) return BatchRowStatus.INVALID
        if (existingKey != null && key == existingKey) return BatchRowStatus.REUSE
        if (keyExistsInXml || collidesWithOtherRow(rows, selfIndex, key, value)) return BatchRowStatus.COLLISION
        if (duplicateValueInBatch(rows, selfIndex, value)) return BatchRowStatus.DUPLICATE
        return BatchRowStatus.NEW
    }

    /** Another included row reuses this key for a different value — writing both would clobber one. */
    private fun collidesWithOtherRow(rows: List<RowFacts>, selfIndex: Int, key: String, value: String): Boolean =
        rows.withIndex().any { (i, other) ->
            i != selfIndex && other.include && other.key == key && other.value != value
        }

    /** Another row already carries the same value (extract once, reuse the key). */
    private fun duplicateValueInBatch(rows: List<RowFacts>, selfIndex: Int, value: String): Boolean =
        rows.withIndex().any { (i, other) -> i != selfIndex && other.value == value }
}
