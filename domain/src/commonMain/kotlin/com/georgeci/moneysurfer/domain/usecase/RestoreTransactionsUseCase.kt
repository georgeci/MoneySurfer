package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import org.koin.core.annotation.Single

/**
 * Puts back what [DeleteTransactionUseCase] removed — the Undo half of a delete.
 *
 * Re-inserting through [ApplyTransactionChangeUseCase] rather than through the repository is the
 * whole point: account balances, the outbox and the search index all move with the row, exactly
 * as they did on the way out. Restoring a transfer therefore restores both of its legs together,
 * because the delete handed both of them over.
 *
 * The rows keep their original ids, so an Undo is a true reversal and not a new transaction that
 * merely looks like the old one.
 */
@Single
class RestoreTransactionsUseCase(
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(transactions: List<Transaction>) {
        transactions.forEach { applyTransactionChange(old = null, new = it) }
    }
}
