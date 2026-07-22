package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [BudgetRepository::class])
class BudgetRepositoryImpl(
    private val dao: BudgetDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : BudgetRepository {

    override fun getAll(): Flow<List<Budget>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: BudgetId): Budget? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(budget: Budget) {
        val entity = budget.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(budget: Budget) {
        val existingCreatedAt = dao.getById(budget.id.value)?.createdAt
        val entity = budget.toEntity().let { mapped ->
            mapped.copy(
                createdAt = existingCreatedAt ?: mapped.createdAt,
                updatedAt = clock.now().toEpochMilliseconds(),
            )
        }
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun setActive(id: BudgetId, isActive: Boolean) {
        val existing = dao.getById(id.value) ?: return
        val entity = existing.copy(
            isActive = isActive,
            updatedAt = clock.now().toEpochMilliseconds(),
        )
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: BudgetId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.BUDGET,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    private suspend fun enqueueUpsert(entity: BudgetEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.BUDGET,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun BudgetEntity.toDomain() = Budget(
        id = BudgetId(id),
        workspaceId = WorkspaceId(workspaceId),
        name = name,
        categoryIds = categoryIds.parseCategoryIds(),
        amount = Money.fromMinor(amount),
        period = BudgetPeriod.entries.firstOrNull { it.name == period } ?: BudgetPeriod.MONTHLY,
        startDate = timeFormatter.parseLocalDate(startDate),
        alertPercent = alertPercent,
        isActive = isActive,
        rollover = rollover,
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun Budget.toEntity() = BudgetEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        name = name,
        categoryIds = categoryIds.toStorageValue(),
        amount = amount.minor,
        period = period.name,
        startDate = timeFormatter.formatLocalDate(startDate),
        alertPercent = alertPercent,
        isActive = isActive,
        rollover = rollover,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}

private fun String.parseCategoryIds(): List<CategoryId> {
    if (isBlank()) return emptyList()
    return split(',').filter { it.isNotBlank() }.map(::CategoryId)
}

private fun List<CategoryId>.toStorageValue(): String =
    joinToString(",") { categoryId -> categoryId.value }
