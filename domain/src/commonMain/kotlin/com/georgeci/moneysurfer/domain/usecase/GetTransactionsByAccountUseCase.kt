package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class GetTransactionsByAccountUseCase(
    private val transactionRepository: TransactionRepository,
) {

    operator fun invoke(accountId: AccountId): Flow<List<Transaction>> =
        transactionRepository.getByAccountId(accountId)

    /**
     * Newest-first page inside [window]. A `null` [accountId] spans every account, which is what
     * the transactions list uses when it is opened outside an account.
     */
    fun window(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> =
        transactionRepository.getCategorizedWindow(accountId, window, limit)

    /** Per-type, per-currency sums over the whole [window] — see [TransactionRepository.getTotals]. */
    fun totals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> =
        transactionRepository.getTotals(accountId, window)
}
