package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

@Single
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    /**
     * Saves [transaction] over the stored row of the same id.
     *
     * The tombstone lookup is not belt-and-braces: a row can be deleted between the edit screen
     * opening and the user pressing Save — by a pulled delete from another device, or by a swipe on
     * a list still sitting in the back stack. Since issue #346 the deleted row keeps its id, so
     * treating "no live row" as "create" would drive [ApplyTransactionChangeUseCase] into an insert
     * that collides with the surviving primary key, failing the save *after* the balance had
     * already moved.
     *
     * Restoring first is also the right answer for the user: they pressed Save on a row they were
     * editing, and the edit is newer than the delete — the same reasoning by which a peer's newer
     * edit lifts a tombstone on pull. When the id names nothing at all (already purged), `old` stays
     * null and the insert branch is correct.
     */
    suspend operator fun invoke(transaction: Transaction) {
        val old = transactionRepository.getById(transaction.id)
            ?: applyTransactionChange.restore(transaction.id)
        applyTransactionChange(old = old, new = transaction)
    }
}
