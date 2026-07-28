package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.calculateBalanceDelta
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

/**
 * Single writer for any transaction change (create / update / delete / restore). Computes the
 * balance delta against [old] and applies it to the affected accounts, then writes the new tx row
 * (or tombstones it). Outbox enqueue happens inside the repository writes.
 *
 * Sequencing: the account-balance update happens first so a partial failure leaves the
 * cache reflecting the intent (will be reconciled by recalc if it diverges). The Room
 * write path is not wrapped in a single transaction in v1 — the project accepts this risk
 * elsewhere; see md/totatl_calc.md §4.
 *
 * - `old == null && new != null` → create
 * - `old != null && new != null` → update (any field, including status / accountId)
 * - `old != null && new == null` → delete (soft-delete locally — issue #346)
 * - `old == null && new == null` → no-op
 *
 * [restore] is the fourth shape, and the reason it is a named entry point rather than another
 * `invoke` combination: it takes an id, not a `Transaction`, because the row it revives is still
 * in the database and is the authority on its own fields.
 */
@Single
class ApplyTransactionChangeUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(old: Transaction?, new: Transaction?) {
        applyBalanceDelta(old, new)
        when {
            old == null && new != null -> transactionRepository.insert(new)
            old != null && new != null -> transactionRepository.update(new)
            old != null && new == null -> transactionRepository.delete(old.id)
            else -> Unit
        }
    }

    /**
     * Lifts the tombstone on [id] and puts its money back on the affected account, returning the
     * restored transaction — or `null` when there was nothing deleted under that id.
     *
     * The balance moves *after* the row, unlike the write paths above, because the delta is
     * computed from the restored row rather than from a copy the caller held: reading it back is
     * what guarantees the refund matches what actually came back. Nothing happens at all when the
     * repository reports no tombstone, so a second Undo cannot credit the account twice.
     */
    suspend fun restore(id: TransactionId): Transaction? {
        val restored = transactionRepository.restore(id) ?: return null
        applyBalanceDelta(old = null, new = restored)
        return restored
    }

    private suspend fun applyBalanceDelta(old: Transaction?, new: Transaction?) {
        for ((accountId, money) in calculateBalanceDelta(old, new)) {
            accountRepository.applyDelta(accountId, money)
        }
    }
}
