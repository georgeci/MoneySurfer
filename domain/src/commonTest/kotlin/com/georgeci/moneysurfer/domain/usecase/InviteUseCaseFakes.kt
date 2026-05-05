package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Reactive in-memory stand-ins for the invite/member/user repositories.
 *
 * Backed by [MutableStateFlow] so emissions propagate to `combine` / `flow.first()` callers
 * without manual re-emission. Suitable for use cases that touch one or two of these repos
 * each — anything heavier should switch to actual DAO-backed fakes.
 */
class FakeInviteRepository : WorkspaceInviteRepository {
    private val state = MutableStateFlow<List<WorkspaceInvite>>(emptyList())

    val rows: List<WorkspaceInvite> get() = state.value

    fun seed(invite: WorkspaceInvite) {
        state.value = state.value + invite
    }

    override fun getAll(): Flow<List<WorkspaceInvite>> = state.asStateFlow()

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceInvite>> =
        state.map { it.filter { inv -> inv.workspaceId == workspaceId } }

    override fun getByEmail(email: String): Flow<List<WorkspaceInvite>> =
        state.map { it.filter { inv -> inv.email == email } }

    override fun getByTargetUserId(userId: UserId): Flow<List<WorkspaceInvite>> =
        state.map { it.filter { inv -> inv.targetUserId == userId } }

    override suspend fun getById(id: WorkspaceInviteId): WorkspaceInvite? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(invite: WorkspaceInvite) {
        state.value = state.value.filter { it.id != invite.id } + invite
    }

    override suspend fun cancel(id: WorkspaceInviteId) {
        state.value = state.value.map { inv ->
            if (inv.id == id) {
                inv.copy(
                    status = com.georgeci.moneysurfer.domain.model.InviteStatus.CANCELLED,
                    respondedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                    updatedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                )
            } else {
                inv
            }
        }
    }

    override suspend fun markAccepted(id: WorkspaceInviteId) {
        state.value = state.value.map { inv ->
            if (inv.id == id) {
                inv.copy(
                    status = com.georgeci.moneysurfer.domain.model.InviteStatus.ACCEPTED,
                    respondedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                    updatedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                )
            } else {
                inv
            }
        }
    }

    override suspend fun markDeclined(id: WorkspaceInviteId) {
        state.value = state.value.map { inv ->
            if (inv.id == id) {
                inv.copy(
                    status = com.georgeci.moneysurfer.domain.model.InviteStatus.DECLINED,
                    respondedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                    updatedAt = inv.updatedAt + kotlin.time.Duration.parse("1ms"),
                )
            } else {
                inv
            }
        }
    }
}

class FakeMemberRepository : WorkspaceMemberRepository {
    private val state = MutableStateFlow<List<WorkspaceMember>>(emptyList())

    val rows: List<WorkspaceMember> get() = state.value

    fun seed(vararg members: WorkspaceMember) {
        state.value = state.value + members.toList()
    }

    override fun getAll(): Flow<List<WorkspaceMember>> = state.asStateFlow()

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> =
        state.map { it.filter { m -> m.workspaceId == workspaceId } }

    override fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>> =
        state.map { it.filter { m -> m.userId == userId } }

    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? =
        state.value.firstOrNull { it.userId == userId && it.workspaceId == workspaceId }

    override suspend fun insert(member: WorkspaceMember) {
        state.value = state.value + member
    }

    override suspend fun update(member: WorkspaceMember) {
        state.value = state.value.map {
            if (it.userId == member.userId && it.workspaceId == member.workspaceId) member else it
        }
    }

    override suspend fun markRemoved(
        userId: UserId,
        workspaceId: WorkspaceId,
        removedByUserId: UserId?,
    ) {
        state.value = state.value.map { m ->
            if (m.userId == userId && m.workspaceId == workspaceId) {
                m.copy(
                    status = com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus.REMOVED,
                    removedAt = m.updatedAt + kotlin.time.Duration.parse("1ms"),
                    updatedAt = m.updatedAt + kotlin.time.Duration.parse("1ms"),
                )
            } else {
                m
            }
        }
    }

    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) {
        state.value = state.value.map { m ->
            if (m.userId == userId && m.workspaceId == workspaceId) {
                m.copy(
                    status = com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus.LEFT,
                    leftAt = m.updatedAt + kotlin.time.Duration.parse("1ms"),
                    updatedAt = m.updatedAt + kotlin.time.Duration.parse("1ms"),
                )
            } else {
                m
            }
        }
    }
}

class FakeUserRepository(initial: List<User> = emptyList()) : UserRepository {
    private val state = MutableStateFlow(initial)

    fun seed(user: User) {
        state.value = state.value.filter { it.id != user.id } + user
    }

    override fun getAll(): Flow<List<User>> = state.asStateFlow()
    override suspend fun getById(id: UserId): User? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(user: User) { seed(user) }
    override suspend fun update(user: User) { seed(user) }
    override suspend fun upsert(user: User) { seed(user) }
    override suspend fun delete(id: UserId) {
        state.value = state.value.filter { it.id != id }
    }
}
