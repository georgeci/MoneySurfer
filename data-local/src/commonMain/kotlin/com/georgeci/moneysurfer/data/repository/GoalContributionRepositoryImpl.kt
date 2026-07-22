package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.GoalContributionDao
import com.georgeci.moneysurfer.data.db.entity.GoalContributionEntity
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [GoalContributionRepository::class])
class GoalContributionRepositoryImpl(
    private val dao: GoalContributionDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : GoalContributionRepository {

    override fun getByGoalId(goalId: GoalId): Flow<List<GoalContribution>> =
        dao.getByGoalId(goalId.value).map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<GoalContribution>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override fun observeSavedAmount(goalId: GoalId): Flow<Money> =
        dao.observeTotalByGoalId(goalId.value).map(Money::fromMinor)

    override suspend fun savedAmount(goalId: GoalId): Money =
        Money.fromMinor(dao.totalByGoalId(goalId.value))

    override suspend fun getById(id: GoalContributionId): GoalContribution? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(contribution: GoalContribution) {
        val entity = contribution.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(contribution: GoalContribution) {
        val entity = contribution.toEntity().copy(
            createdAt = dao.getById(contribution.id.value)?.createdAt
                ?: contribution.toEntity().createdAt,
            updatedAt = clock.now().toEpochMilliseconds(),
        )
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: GoalContributionId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    private suspend fun enqueueUpsert(
        entity: GoalContributionEntity,
        operation: MutationOperation,
    ) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun GoalContributionEntity.toDomain() = GoalContribution(
        id = GoalContributionId(id),
        workspaceId = WorkspaceId(workspaceId),
        goalId = GoalId(goalId),
        amount = Money.fromMinor(amount),
        occurredOn = timeFormatter.parseLocalDate(occurredOn),
        note = note,
        transferId = transferId?.let { TransferId(it) },
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun GoalContribution.toEntity() = GoalContributionEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        goalId = goalId.value,
        amount = amount.minor,
        occurredOn = timeFormatter.formatLocalDate(occurredOn),
        note = note,
        transferId = transferId?.value,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}
