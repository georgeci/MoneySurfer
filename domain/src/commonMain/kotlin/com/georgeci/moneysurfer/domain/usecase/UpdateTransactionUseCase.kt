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
    }
}
