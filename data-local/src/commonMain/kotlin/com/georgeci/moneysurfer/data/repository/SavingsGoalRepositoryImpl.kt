package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.GoalContributionDao
import com.georgeci.moneysurfer.data.db.dao.GoalDao
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [SavingsGoalRepository::class])
class SavingsGoalRepositoryImpl(
    private val dao: GoalDao,
    private val contributionDao: GoalContributionDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : SavingsGoalRepository {

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<SavingsGoal>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: GoalId): SavingsGoal? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(goal: SavingsGoal) {
        val entity = goal.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(goal: SavingsGoal) {
        val entity = goal.toEntity().copy(
            createdAt = dao.getById(goal.id.value)?.createdAt ?: goal.toEntity().createdAt,
            updatedAt = clock.now().toEpochMilliseconds(),
        )
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    /**
     * Reads the contributions BEFORE the delete: Room's `ON DELETE CASCADE` removes
     * them without a DAO call, so this is the last moment their ids are knowable.
     * Without these enqueues the remote rows would survive as orphans under a goal
     * that no longer exists.
     */
    override suspend fun delete(id: GoalId) {
        val existing = dao.getById(id.value) ?: return
        val contributions = contributionDao.listByGoalId(id.value)
        dao.delete(id.value)
        contributions.forEach { contribution ->
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
                entityId = contribution.id,
                scopeKey = contribution.workspaceId,
            )
        }
        outboxEnqueuer.enqueueDelete(
            entityType = SyncEntityTypes.GOAL,
            entityId = existing.id,
            scopeKey = existing.workspaceId,
        )
    }

    private suspend fun enqueueUpsert(entity: GoalEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.GOAL,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun GoalEntity.toDomain() = SavingsGoal(
        id = GoalId(id),
        workspaceId = WorkspaceId(workspaceId),
        title = title,
        emoji = emoji,
        hue = hue,
        target = Money.fromMinor(target),
        currencyCode = CurrencyCode(currencyCode),
        startDate = timeFormatter.parseLocalDate(startDate),
        targetDate = targetDate?.let { timeFormatter.parseLocalDateOrNull(it) },
        accountId = accountId?.let { AccountId(it) },
        status = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.ACTIVE),
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun SavingsGoal.toEntity() = GoalEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        title = title,
        emoji = emoji,
        hue = hue,
        target = target.minor,
        currencyCode = currencyCode.value,
        startDate = timeFormatter.formatLocalDate(startDate),
        targetDate = targetDate?.let { timeFormatter.formatLocalDate(it) },
        accountId = accountId?.value,
        status = status.name,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}
