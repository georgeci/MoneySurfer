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

    /** Deletes the transaction and returns the removed row so callers can offer an Undo. */
    suspend operator fun invoke(id: TransactionId): Transaction? {
        val old = transactionRepository.getById(id) ?: return null
        applyTransactionChange(old = old, new = null)
        return old
    }
}
