package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.AccountDao
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import com.georgeci.moneysurfer.domain.primitives.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [AccountRepository::class])
class AccountRepositoryImpl(
    private val dao: AccountDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: Clock,
) : AccountRepository {

    override fun getAll(): Flow<List<Account>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: AccountId): Account? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(account: Account) {
        val entity = account.toEntity().copy(updatedAt = nowMillis())
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(account: Account) {
        val entity = account.toEntity().copy(updatedAt = nowMillis())
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: AccountId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.ACCOUNT,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        if (delta.isZero()) return
        dao.applyDelta(accountId.value, delta.minor, nowMillis())
    }

    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        dao.setBalance(accountId.value, balance.minor, nowMillis())
    }

    private suspend fun enqueueUpsert(entity: AccountEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun nowMillis(): Long = clock.nowMillis()
}

private fun AccountEntity.toDomain() = Account(
    id = AccountId(id),
    workspaceId = WorkspaceId(workspaceId),
    name = name,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.SAVINGS),
    currencyCode = CurrencyCode(currency),
    balance = Money.fromMinor(balance),
    updatedAt = updatedAt.toInstant(),
)

private fun Account.toEntity() = AccountEntity(
    id = id.value,
    workspaceId = workspaceId.value,
    name = name,
    type = type.name,
    currency = currencyCode.value,
    balance = balance.minor,
    updatedAt = updatedAt.toEpochMillis(),
)
