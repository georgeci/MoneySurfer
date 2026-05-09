package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

@Single(binds = [TransactionRepository::class])
class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : TransactionRepository {

    override fun getAll(): Flow<List<Transaction>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> =
        dao.getAllCategorized().map { list -> list.map { it.toDomain() } }

    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        dao.getByAccountId(accountId.value).map { list -> list.map { it.toDomain() } }

    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        dao.getByAccountIdCategorized(accountId.value).map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: TransactionId): Transaction? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(transaction: Transaction) {
        val entity = transaction.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(transaction: Transaction) {
        val existingCreatedAt = dao.getById(transaction.id.value)?.createdAt
        val entity = transaction.toEntity().copy(
            createdAt = existingCreatedAt ?: transaction.toEntity().createdAt,
            updatedAt = clock.now().toEpochMilliseconds(),
        )
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: TransactionId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.TRANSACTION,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    private suspend fun enqueueUpsert(entity: TransactionEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.TRANSACTION,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun TransactionEntity.toDomain() = Transaction(
        id = TransactionId(id),
        workspaceId = WorkspaceId(workspaceId),
        accountId = AccountId(accountId),
        money = Money.fromMinor(amount),
        currencyCode = CurrencyCode(currencyCode),
        categoryId = categoryId?.let(::CategoryId),
        note = note,
        operationAt = timeFormatter.parseInstant(operationAt),
        operationDate = resolveOperationDate(operationDate, operationAt),
        type = parseType(type, amount),
        status = parseStatus(status),
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
        transferId = transferId?.let(::TransferId),
    )

    private fun CategorizedTransactionEntity.toDomain() = CategorizedTransaction(
        transaction = Transaction(
            id = TransactionId(id),
            workspaceId = WorkspaceId(workspaceId),
            accountId = AccountId(accountId),
            money = Money.fromMinor(amount),
            currencyCode = CurrencyCode(currencyCode),
            categoryId = categoryId?.let(::CategoryId),
            note = note,
            operationAt = timeFormatter.parseInstant(operationAt),
            operationDate = resolveOperationDate(operationDate, operationAt),
            type = parseType(type, amount),
            status = parseStatus(status),
            createdAt = timeFormatter.parseInstant(createdAt),
            updatedAt = timeFormatter.parseInstant(updatedAt),
            transferId = transferId?.let(::TransferId),
        ),
        categoryName = categoryName,
    )

    private fun resolveOperationDate(stored: String, operationAt: Long) =
        resolveLegacyOperationDate(timeFormatter, stored, operationAt)

    private fun Transaction.toEntity() = TransactionEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        accountId = accountId.value,
        amount = money.minor,
        currencyCode = currencyCode.value,
        categoryId = categoryId?.value,
        note = note,
        operationAt = timeFormatter.formatInstant(operationAt),
        operationDate = timeFormatter.formatLocalDate(operationDate),
        type = type.name,
        status = status.name,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
        transferId = transferId?.value,
    )
}

private fun parseType(raw: String, amount: Long): TransactionType =
    when (raw) {
        TransactionType.INCOME.name -> TransactionType.INCOME
        TransactionType.EXPENSE.name -> TransactionType.EXPENSE
        TransactionType.OPENING_BALANCE.name -> TransactionType.OPENING_BALANCE
        "INITIAL_BALANCE" -> TransactionType.OPENING_BALANCE
        "REGULAR" -> if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME
        else -> TransactionType.EXPENSE
    }

private fun parseStatus(raw: String): TransactionStatus =
    runCatching { TransactionStatus.valueOf(raw) }.getOrDefault(TransactionStatus.ACTUAL)

// Legacy rows may have a blank `operationDate` (older schema). Resolve via UTC so the
// same epoch maps to the same business date on every device — matches the fallback in
// SyncDtoMappers and prevents cross-device divergence on push-back.
internal fun resolveLegacyOperationDate(
    timeFormatter: TimeFormatter,
    stored: String,
    operationAt: Long,
) = timeFormatter.parseLocalDateOrNull(stored)
    ?: timeFormatter.parseInstant(operationAt).toLocalDateTime(TimeZone.UTC).date
