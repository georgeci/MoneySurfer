package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.calculateBalanceDelta
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

/**
 * Single writer for any transaction change (create / update / delete). Computes the balance
 * delta against [old] and applies it to the affected accounts, then writes the new tx row
 * (or hard-deletes it). Outbox enqueue happens inside the repository writes.
 *
 * Sequencing: the account-balance update happens first so a partial failure leaves the
 * cache reflecting the intent (will be reconciled by recalc if it diverges). The Room
 * write path is not wrapped in a single transaction in v1 — the project accepts this risk
 * elsewhere; see md/totatl_calc.md §4.
 *
 * - `old == null && new != null` → create
 * - `old != null && new != null` → update (any field, including status / accountId)
 * - `old != null && new == null` → delete (hard-delete locally)
 * - `old == null && new == null` → no-op
 */
@Single
class ApplyTransactionChangeUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(old: Transaction?, new: Transaction?) {
        val delta = calculateBalanceDelta(old, new)
        for ((accountId, money) in delta) {
            accountRepository.applyDelta(accountId, money)
        }
        when {
            old == null && new != null -> transactionRepository.insert(new)
            old != null && new != null -> transactionRepository.update(new)
            old != null && new == null -> transactionRepository.delete(old.id)
            else -> Unit
        }
    }
}
