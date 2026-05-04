package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single(binds = [WorkspaceInviteRepository::class])
class WorkspaceInviteRepositoryImpl(
    private val dao: WorkspaceInviteDao,
    private val outboxEnqueuer: OutboxEnqueuer,
) : WorkspaceInviteRepository {

    private val log = Logger.withTag(TAG)

    override fun getAll(): Flow<List<WorkspaceInvite>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceInvite>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override fun getByEmail(email: String): Flow<List<WorkspaceInvite>> =
        dao.getByEmail(email).map { list -> list.map { it.toDomain() } }

    override fun getByTargetUserId(userId: UserId): Flow<List<WorkspaceInvite>> =
        dao.getByTargetUserId(userId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: WorkspaceInviteId): WorkspaceInvite? =
        dao.getById(id.value)?.toDomain()

    override suspend fun upsert(invite: WorkspaceInvite) {
        val now = now()
        val isNew = dao.getById(invite.id.value) == null
        val entity = invite.toEntity().copy(updatedAt = now)
        dao.upsert(entity)
        log.i {
            "[upsert] id=${entity.id} wid=${entity.workspaceId} email=${entity.email} " +
                "status=${entity.status} role=${entity.role} target=${entity.targetUserId} " +
                "isNew=$isNew updatedAt=${entity.updatedAt} expiresAt=${entity.expiresAt}"
        }
        enqueueUpsert(
            entity = entity,
            operation = if (isNew) MutationOperation.INSERT else MutationOperation.UPDATE,
        )
    }

    override suspend fun cancel(id: WorkspaceInviteId) {
        transition(id, InviteStatus.CANCELLED)
    }

    override suspend fun markAccepted(id: WorkspaceInviteId) {
        transition(id, InviteStatus.ACCEPTED)
    }

    override suspend fun markDeclined(id: WorkspaceInviteId) {
        transition(id, InviteStatus.DECLINED)
    }

    private suspend fun transition(id: WorkspaceInviteId, target: InviteStatus) {
        val current = dao.getById(id.value)
        if (current == null) {
            log.w { "[transition] id=${id.value} not found locally — skipping $target" }
            return
        }
        val now = now()
        val updated = current.copy(
            status = target.name,
            respondedAt = now,
            updatedAt = now,
        )
        dao.upsert(updated)
        log.i {
            "[transition] id=${updated.id} wid=${updated.workspaceId} ${current.status}→$target " +
                "email=${updated.email} respondedAt=${updated.respondedAt}"
        }
        enqueueUpsert(updated, MutationOperation.UPDATE)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private suspend fun enqueueUpsert(entity: WorkspaceInviteEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.WORKSPACE_INVITE,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
        log.i { "[enqueue] id=${entity.id} wid=${entity.workspaceId} op=$operation" }
    }

    private companion object {
        const val TAG = "InviteRepo"
    }
}

private fun WorkspaceInviteEntity.toDomain() = WorkspaceInvite(
    id = WorkspaceInviteId(id),
    workspaceId = WorkspaceId(workspaceId),
    email = email,
    targetUserId = targetUserId?.let(::UserId),
    role = WorkspaceRole.entries.firstOrNull { it.name == role } ?: WorkspaceRole.VIEWER,
    status = InviteStatus.entries.firstOrNull { it.name == status } ?: InviteStatus.PENDING,
    invitedByUserId = UserId(invitedByUserId),
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    respondedAt = respondedAt,
)

private fun WorkspaceInvite.toEntity() = WorkspaceInviteEntity(
    id = id.value,
    workspaceId = workspaceId.value,
    email = email,
    targetUserId = targetUserId?.value,
    role = role.name,
    status = status.name,
    invitedByUserId = invitedByUserId.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    respondedAt = respondedAt,
)
