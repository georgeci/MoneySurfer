package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.balanceImpact
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Repair: recompute `Account.balance` from authoritative transaction history. Used after
 * migration, manual repair, or when a divergence detector fires.
 *
 * Should only run when the outbox is empty for the workspace, otherwise a parallel
 * `applyDelta` race produces inconsistent state. The caller (sync coordinator / repair UI)
 * is responsible for that gate; this use case is intentionally simple.
 */
@Single
class RecalculateAccountBalanceUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(accountId: AccountId): Money {
        val all = transactionRepository.getByAccountId(accountId).first()
        var sumMinor = 0L
        for (tx in all) {
            val impact = tx.balanceImpact()[accountId] ?: continue
            sumMinor += impact.minor
        }
        val balance = Money.fromMinor(sumMinor)
        accountRepository.setBalance(accountId, balance)
        return balance
    }
}
