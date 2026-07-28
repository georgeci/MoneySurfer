package com.georgeci.moneysurfer.domain.repositories

import kotlin.time.Instant

/**
 * Storage side of the tombstone retention policy (issue #346).
 *
 * Split off [TransactionRepository] rather than added to it because purging is maintenance, not
 * a transaction operation: it has no domain model to map, no caller on any screen, and every
 * test double of the transaction store would otherwise have to answer a question none of them
 * ask. See `PurgeDeletedTransactionsUseCase` for the policy that drives it.
 */
fun interface TransactionRetentionRepository {

    /**
     * Removes the rows tombstoned strictly before [threshold] for good, and returns how many
     * went. Live rows are never touched.
     */
    suspend fun purgeDeletedBefore(threshold: Instant): Int
}
