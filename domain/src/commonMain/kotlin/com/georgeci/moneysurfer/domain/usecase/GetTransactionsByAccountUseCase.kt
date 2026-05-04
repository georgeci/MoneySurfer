package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class GetTransactionsByAccountUseCase(
    private val transactionRepository: TransactionRepository,
) {

    operator fun invoke(accountId: AccountId): Flow<List<Transaction>> =
        transactionRepository.getByAccountId(accountId)

    fun categorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        transactionRepository.getByAccountIdCategorized(accountId)
}
