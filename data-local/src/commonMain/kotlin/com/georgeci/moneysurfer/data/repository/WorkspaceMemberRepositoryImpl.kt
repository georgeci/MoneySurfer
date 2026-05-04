package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single(binds = [WorkspaceMemberRepository::class])
class WorkspaceMemberRepositoryImpl(
    private val dao: WorkspaceMemberDao,
    private val outboxEnqueuer: OutboxEnqueuer,
) : WorkspaceMemberRepository {

    override fun getAll(): Flow<List<WorkspaceMember>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>> =
        dao.getByUserId(userId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? =
        dao.getById(userId.value, workspaceId.value)?.toDomain()

    override suspend fun insert(member: WorkspaceMember) {
        val entity = member.toEntity().copy(updatedAt = now())
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(member: WorkspaceMember) {
        val entity = member.toEntity().copy(updatedAt = now())
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun markRemoved(
        userId: UserId,
        workspaceId: WorkspaceId,
        removedByUserId: UserId?,
    ) {
        val current = dao.getById(userId.value, workspaceId.value) ?: return
        val now = now()
        val updated = current.copy(
            status = WorkspaceMemberStatus.REMOVED.name,
            removedAt = now,
            updatedAt = now,
        )
        dao.upsert(updated)
        enqueueUpsert(updated, MutationOperation.UPDATE)
    }

    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) {
        val current = dao.getById(userId.value, workspaceId.value) ?: return
        val now = now()
        val updated = current.copy(
            status = WorkspaceMemberStatus.LEFT.name,
            leftAt = now,
            updatedAt = now,
        )
        dao.upsert(updated)
        enqueueUpsert(updated, MutationOperation.UPDATE)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private suspend fun enqueueUpsert(entity: WorkspaceMemberEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.WORKSPACE_MEMBER,
            entityId = entity.userId,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }
}

private fun WorkspaceMemberEntity.toDomain() = WorkspaceMember(
    userId = UserId(userId),
    workspaceId = WorkspaceId(workspaceId),
    role = WorkspaceRole.entries.firstOrNull { it.name == role } ?: WorkspaceRole.VIEWER,
    status = WorkspaceMemberStatus.entries.firstOrNull { it.name == status }
        ?: WorkspaceMemberStatus.ACTIVE,
    displayName = displayName,
    email = email,
    addedByUserId = addedByUserId?.let(::UserId),
    createdAt = createdAt,
    updatedAt = updatedAt,
    leftAt = leftAt,
    removedAt = removedAt,
)

private fun WorkspaceMember.toEntity() = WorkspaceMemberEntity(
    userId = userId.value,
    workspaceId = workspaceId.value,
    role = role.name,
    status = status.name,
    displayName = displayName,
    email = email,
    addedByUserId = addedByUserId?.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
    leftAt = leftAt,
    removedAt = removedAt,
)
