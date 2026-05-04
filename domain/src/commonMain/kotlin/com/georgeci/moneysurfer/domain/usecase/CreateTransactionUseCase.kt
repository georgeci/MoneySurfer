package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import org.koin.core.annotation.Single

@Single
class CreateTransactionUseCase(
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(transaction: Transaction) {
        applyTransactionChange(old = null, new = transaction)
    }
}
