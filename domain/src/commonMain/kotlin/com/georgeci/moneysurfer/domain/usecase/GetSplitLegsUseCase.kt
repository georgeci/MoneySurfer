package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

/**
 * Every leg of the receipt [transaction] belongs to, or an empty list when it is not part of a
 * split — the breakdown a details screen renders under the amount.
 *
 * A one-leg result is possible and is not a broken group: a leg can arrive from sync or a CSV
 * import before (or without) its siblings, and it is a complete transaction on its own. Callers
 * treat "fewer than two legs" as "nothing to break down", not as an error.
 */
@Single
class GetSplitLegsUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(transaction: Transaction): List<Transaction> {
        val splitId = transaction.splitId ?: return emptyList()
        return transactionRepository.getBySplitId(splitId)
    }
}
