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
        realignSplitSiblings(transaction)
    }

    /**
     * Carries the fields a split's legs share onto the siblings of an edited leg.
     *
     * The legs of a split are one receipt, so they have to agree on account, currency, business
     * date, moment and type — differ on any of those and the group stops describing a single
     * payment, which is exactly what the collapsed list row and the details breakdown assume. The
     * ordinary edit screen edits one leg and knows nothing about splits, so the invariant is
     * re-established here rather than defended by rejecting the edit: refusing to move a receipt to
     * the right date because it happens to be split would be a worse answer than moving every leg.
     *
     * Category and amount are deliberately not propagated — they are the whole point of the legs. A
     * sibling already in step is left untouched, so the ordinary single-category edit still enqueues
     * exactly one row for sync.
     */
    private suspend fun realignSplitSiblings(edited: Transaction) {
        val splitId = edited.splitId ?: return
        transactionRepository.getBySplitId(splitId)
            .filter { it.id != edited.id }
            .forEach { sibling ->
                val realigned = sibling.copy(
                    accountId = edited.accountId,
                    currencyCode = edited.currencyCode,
                    operationAt = edited.operationAt,
                    operationDate = edited.operationDate,
                    type = edited.type,
                )
                if (realigned != sibling) {
                    applyTransactionChange(old = sibling, new = realigned)
                }
            }
    }
}
