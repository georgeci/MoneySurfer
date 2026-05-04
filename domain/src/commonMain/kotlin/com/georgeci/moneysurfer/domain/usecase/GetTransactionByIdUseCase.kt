package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

@Single
class GetTransactionByIdUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(id: TransactionId): Transaction? {
        return transactionRepository.getById(id)
    }
}
