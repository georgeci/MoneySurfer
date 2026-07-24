package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import org.koin.core.annotation.Single

/**
 * The other leg of the transfer [transaction] belongs to, or `null` when it is not a transfer
 * at all or its pair is missing.
 *
 * A transfer is two ordinary rows sharing a `transferId` — an `EXPENSE` on the source account
 * and an `INCOME` on the destination (see `CreateTransferUseCase`). Neither row knows the other
 * account, so anything that wants to say "from X to Y" has to resolve the sibling first.
 *
 * A missing pair is a legitimate state, not a bug to throw on: a leg can arrive from sync or a
 * CSV import before (or without) its counterpart.
 */
@Single
class GetTransferCounterpartUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(transaction: Transaction): Transaction? {
        val transferId = transaction.transferId ?: return null
        return transactionRepository.getByTransferId(transferId)
            .firstOrNull { it.id != transaction.id }
    }
}
