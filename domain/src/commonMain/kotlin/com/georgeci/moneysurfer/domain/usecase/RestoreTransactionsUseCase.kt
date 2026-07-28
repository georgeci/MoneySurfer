package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import org.koin.core.annotation.Single

/**
 * Puts back what [DeleteTransactionUseCase] removed — the Undo half of a delete.
 *
 * Since issue #346 a delete only tombstones the row, so this lifts the tombstone rather than
 * re-inserting anything: one UPDATE per row, no round trip through insert, and the id, the
 * `createdAt` and every field the user typed come back untouched because they never left. Only the
 * ids of [transactions] are read — the copies themselves are how the caller remembers *which* rows
 * to bring back, not the data being restored.
 *
 * Going through [ApplyTransactionChangeUseCase] rather than the repository is still the point:
 * account balances, the outbox and the search index all move with the row, exactly as they did on
 * the way out. Restoring a transfer therefore restores both of its legs together, because the
 * delete handed both of them over.
 *
 * A row that is no longer tombstoned — already restored, or purged once its retention window ran
 * out — is skipped rather than recreated, so a doubled Undo cannot credit an account twice.
 */
@Single
class RestoreTransactionsUseCase(
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(transactions: List<Transaction>) {
        transactions.forEach { applyTransactionChange.restore(it.id) }
    }
}
