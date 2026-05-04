package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

@Single
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(id: TransactionId) {
        val old = transactionRepository.getById(id) ?: return
        applyTransactionChange(old = old, new = null)
    }
}
