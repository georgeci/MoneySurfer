package com.georgeci.moneysurfer.sync.internal.repository

import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [PendingMutationQueue::class])
class PendingMutationQueueImpl(
    private val dao: PendingMutationDao,
) : PendingMutationQueue {

    override suspend fun enqueue(mutation: PendingMutation) {
        dao.insert(mutation.toEntity(status = PendingMutationEntity.STATUS_PENDING))
    }

    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> =
        dao.pending(workspaceId = workspaceFilterFor(scope), limit = limit)
            .map { it.toDomain() }

    override suspend fun markInFlight(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markInFlight(ids)
    }

    override suspend fun markCompleted(ids: List<String>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error)
    }

    override val pendingCount: Flow<Int> = dao.pendingCount()

    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> =
        dao.observeAll(limit).map { rows -> rows.map { it.toDomain() } }

    private fun workspaceFilterFor(scope: SyncScope): String? = when (scope) {
        SyncScope.UploadOnly,
        SyncScope.ActiveWorkspace,
        SyncScope.AllUserData,
        SyncScope.ChangedSinceLastSync,
        -> null
    }
}

private fun PendingMutation.toEntity(status: String) = PendingMutationEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation.name,
    workspaceId = scopeKey,
    createdAt = createdAt.toEpochMilliseconds(),
    attempts = attempts,
    status = status,
    lastError = lastError,
)

private fun PendingMutationEntity.toDomain() = PendingMutation(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = MutationOperation.valueOf(operation),
    scopeKey = workspaceId,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    attempts = attempts,
    lastError = lastError,
)
