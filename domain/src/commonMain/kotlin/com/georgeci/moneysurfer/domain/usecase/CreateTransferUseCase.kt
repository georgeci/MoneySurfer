package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
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
        val transferCategoryId = ensureTransferCategory(params.from.workspaceId, now)

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

    /**
     * Resolve the workspace's seeded "Transfer" system category, creating it lazily for
     * workspaces that pre-date the seed. Without this, both legs would persist with a
     * `null` categoryId and break the "auto-tag transfers" contract.
     */
    private suspend fun ensureTransferCategory(
        workspaceId: WorkspaceId,
        now: Instant,
    ): CategoryId {
        val existing = categoryRepository
            .getByWorkspaceId(workspaceId)
            .first()
            .firstOrNull { it.systemKind == CategorySystemKind.TRANSFER }
        if (existing != null) return existing.id

        val seeded = Category(
            id = CategoryId.uuid(),
            workspaceId = workspaceId,
            name = TRANSFER_CATEGORY_NAME,
            type = CategoryType.TRANSFER,
            parentId = null,
            createdAt = now,
            updatedAt = now,
            systemKind = CategorySystemKind.TRANSFER,
        )
        categoryRepository.insert(seeded)
        return seeded.id
    }

    private companion object {
        const val TRANSFER_CATEGORY_NAME = "Transfer"
    }
}
