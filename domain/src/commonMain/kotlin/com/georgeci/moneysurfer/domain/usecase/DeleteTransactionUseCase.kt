package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

@Single
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    /**
     * Deletes the transaction and returns every row removed, so callers can offer an Undo by
     * handing the list straight back to [RestoreTransactionsUseCase].
     *
     * A transfer is deleted whole. Its two legs are one movement of money split across two rows,
     * and removing only the one the user swiped would leave the other account credited out of
     * nowhere — a corrupt balance rather than a partly-finished delete.
     *
     * Returns an empty list when [id] names nothing, which is also how a caller knows there is
     * nothing to offer an Undo for.
     */
    suspend operator fun invoke(id: TransactionId): List<Transaction> {
        val target = transactionRepository.getById(id) ?: return emptyList()
        val rows = target.transferId
            ?.let { transactionRepository.getByTransferId(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(target)
        rows.forEach { applyTransactionChange(old = it, new = null) }
        return rows
    }
}
