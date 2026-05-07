package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class CreateTransferUseCase(
    private val categoryRepository: CategoryRepository,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {
    data class Params(
        val from: Account,
        val to: Account,
        val fromMoney: Money,
        val toMoney: Money,
        val note: String,
        val operationAt: Instant,
        val operationDate: LocalDate,
    )

    suspend operator fun invoke(params: Params) {
        require(params.from.id != params.to.id) { "Transfer source and destination must differ" }

        val now = getCurrentTime()
        val transferId = TransferId.uuid()
        val transferCategoryId = categoryRepository
            .getByWorkspaceId(params.from.workspaceId)
            .first()
            .firstOrNull { it.systemKind == CategorySystemKind.TRANSFER }
            ?.id

        val outLeg = Transaction(
            id = TransactionId.uuid(),
            workspaceId = params.from.workspaceId,
            accountId = params.from.id,
            money = params.fromMoney.abs(),
            currencyCode = params.from.currencyCode,
            categoryId = transferCategoryId,
            note = params.note,
            operationAt = params.operationAt,
            operationDate = params.operationDate,
            type = TransactionType.EXPENSE,
            createdAt = now,
            updatedAt = now,
            transferId = transferId,
        )
        val inLeg = Transaction(
            id = TransactionId.uuid(),
            workspaceId = params.to.workspaceId,
            accountId = params.to.id,
            money = params.toMoney.abs(),
            currencyCode = params.to.currencyCode,
            categoryId = transferCategoryId,
            note = params.note,
            operationAt = params.operationAt,
            operationDate = params.operationDate,
            type = TransactionType.INCOME,
            createdAt = now,
            updatedAt = now,
            transferId = transferId,
        )

        applyTransactionChange(old = null, new = outLeg)
        applyTransactionChange(old = null, new = inLeg)
    }
}
