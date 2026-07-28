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
     * A split is deleted whole for the same reason read as the user sees it: the group is one
     * receipt shown as one row, so deleting that row deletes the payment — not the two thirds of it
     * that happened to be filed under Groceries. The remaining legs would otherwise stay behind
     * with no collapsed row left to reveal them.
     *
     * Returns an empty list when [id] names nothing, which is also how a caller knows there is
     * nothing to offer an Undo for.
     */
    suspend operator fun invoke(id: TransactionId): List<Transaction> {
        val target = transactionRepository.getById(id) ?: return emptyList()
        // A row always matches its own transferId / splitId, so either lookup returns the target
        // plus its siblings — no fallback needed for the grouped cases. The two groupings are
        // mutually exclusive in practice (a transfer leg carries the system Transfer category and
        // is never split), and transfers win if a foreign writer ever sets both.
        val rows = target.transferId?.let { transactionRepository.getByTransferId(it) }
            ?: target.splitId?.let { transactionRepository.getBySplitId(it) }
            ?: listOf(target)
        rows.forEach { applyTransactionChange(old = it, new = null) }
        return rows
    }
}
