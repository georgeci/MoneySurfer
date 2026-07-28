package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

@Single
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(transaction: Transaction) {
        val old = transactionRepository.getById(transaction.id)
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
