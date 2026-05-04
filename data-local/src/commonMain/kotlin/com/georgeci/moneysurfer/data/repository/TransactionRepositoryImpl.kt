package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.entity.CategorizedTransactionEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import com.georgeci.moneysurfer.domain.primitives.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [TransactionRepository::class])
class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: Clock,
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
        val now = nowMillis()
        val entity = transaction.toEntity().copy(createdAt = now, updatedAt = now)
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(transaction: Transaction) {
        // Preserve the row's original createdAt — domain object may have been
        // reconstructed without it (defaults to operationAt). Audit invariant.
        val existingCreatedAt = dao.getById(transaction.id.value)?.createdAt
        val entity = transaction.toEntity().copy(
            createdAt = existingCreatedAt ?: transaction.toEntity().createdAt,
            updatedAt = nowMillis(),
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

    private fun nowMillis(): Long = clock.nowMillis()
}

private fun TransactionEntity.toDomain() = Transaction(
    id = TransactionId(id),
    workspaceId = WorkspaceId(workspaceId),
    accountId = AccountId(accountId),
    money = Money.fromMinor(amount),
    currencyCode = CurrencyCode(currencyCode),
    categoryId = categoryId?.let(::CategoryId),
    note = note,
    operationAt = operationAt.toInstant(),
    operationDate = operationDate.toLocalDateOrNull()
        ?: operationAt.toInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    type = parseType(type, amount),
    status = parseStatus(status),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
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
        operationAt = operationAt.toInstant(),
        operationDate = operationDate.toLocalDate(),
        type = parseType(type, amount),
        status = parseStatus(status),
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
    ),
    categoryName = categoryName,
)

private fun Transaction.toEntity() = TransactionEntity(
    id = id.value,
    workspaceId = workspaceId.value,
    accountId = accountId.value,
    amount = money.minor,
    currencyCode = currencyCode.value,
    categoryId = categoryId?.value,
    note = note,
    operationAt = operationAt.toEpochMillis(),
    operationDate = operationDate.toIsoDate(),
    type = type.name,
    status = status.name,
    createdAt = createdAt.toEpochMillis(),
    updatedAt = updatedAt.toEpochMillis(),
)

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
