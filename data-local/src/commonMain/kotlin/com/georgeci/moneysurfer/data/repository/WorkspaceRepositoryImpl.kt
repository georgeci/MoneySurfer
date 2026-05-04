package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.WorkspaceDao
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant

@Single(binds = [WorkspaceRepository::class])
class WorkspaceRepositoryImpl(
    private val dao: WorkspaceDao,
    private val outboxEnqueuer: OutboxEnqueuer,
) : WorkspaceRepository {

    override fun getAll(): Flow<List<Workspace>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByUserId(userId: UserId): Flow<List<Workspace>> =
        dao.getByUserId(userId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: WorkspaceId): Workspace? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(workspace: Workspace) {
        val entity = workspace.toEntity().copy(updatedAt = Clock.System.now().toEpochMilliseconds())
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(workspace: Workspace) {
        val entity = workspace.toEntity().copy(updatedAt = Clock.System.now().toEpochMilliseconds())
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: WorkspaceId) {
        dao.delete(id.value)
        outboxEnqueuer.enqueueDelete(
            entityType = SyncEntityTypes.WORKSPACE,
            entityId = id.value,
            scopeKey = id.value,
        )
    }

    private suspend fun enqueueUpsert(entity: WorkspaceEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.WORKSPACE,
            entityId = entity.id,
            scopeKey = entity.id,
            operation = operation,
        )
    }
}

private fun WorkspaceEntity.toDomain() = Workspace(
    id = WorkspaceId(id),
    name = name,
    description = description,
    baseCurrency = CurrencyCode(baseCurrency),
    ownerId = UserId(ownerId),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    archived = archived,
)

private fun Workspace.toEntity() = WorkspaceEntity(
    id = id.value,
    name = name,
    description = description,
    baseCurrency = baseCurrency.value,
    ownerId = ownerId.value,
    createdAt = createdAt.toEpochMilliseconds(),
    archived = archived,
)
