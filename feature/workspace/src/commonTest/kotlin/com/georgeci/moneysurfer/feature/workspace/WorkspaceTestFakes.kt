package com.georgeci.moneysurfer.feature.workspace

import arrow.core.Either
import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal const val UNUSED = "collaborator not exercised by the workspace view model tests"

/**
 * Replay-1 in-memory workspace store: every write re-emits, so a view model observing
 * [getByUserId] sees the rows it just caused to be written.
 *
 * [failOnInsert] / [failOnUpdate] make the two write paths fail on demand — the creation screen
 * has a distinct error branch for each, and there is no other way to reach them.
 */
internal class FakeWorkspaceRepository(
    initial: List<Workspace> = emptyList(),
    private val failOnInsert: Boolean = false,
    private val failOnUpdate: Boolean = false,
) : WorkspaceRepository {
    private val flow = MutableStateFlow(initial)

    var inserts = 0
        private set

    fun snapshot(): List<Workspace> = flow.value

    fun emit(workspaces: List<Workspace>) {
        flow.value = workspaces
    }

    override fun getAll(): Flow<List<Workspace>> = flow
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flow
    override suspend fun getById(id: WorkspaceId): Workspace? = flow.value.firstOrNull { it.id == id }

    override suspend fun insert(workspace: Workspace) {
        if (failOnInsert) error("simulated local workspace insert failure")
        inserts++
        flow.value = flow.value + workspace
    }

    override suspend fun update(workspace: Workspace) {
        if (failOnUpdate) error("simulated local workspace update failure")
        flow.value = flow.value.map { if (it.id == workspace.id) workspace else it }
    }

    override suspend fun delete(id: WorkspaceId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

internal class FakeWorkspaceMemberRepository : WorkspaceMemberRepository {
    private val flow = MutableStateFlow(emptyList<WorkspaceMember>())

    override fun getAll(): Flow<List<WorkspaceMember>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> = flow
    override fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>> = flow
    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? =
        flow.value.firstOrNull { it.userId == userId && it.workspaceId == workspaceId }

    override suspend fun insert(member: WorkspaceMember) {
        flow.value = flow.value + member
    }

    override suspend fun update(member: WorkspaceMember) {
        flow.value = flow.value.map {
            if (it.userId == member.userId && it.workspaceId == member.workspaceId) member else it
        }
    }

    override suspend fun markRemoved(userId: UserId, workspaceId: WorkspaceId, removedByUserId: UserId?) = Unit
    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) = Unit
}

internal class FakeCategoryRepository : CategoryRepository {
    private val flow = MutableStateFlow(emptyList<Category>())

    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = flow.value.firstOrNull { it.id == id }

    override suspend fun insert(category: Category) {
        flow.value = flow.value + category
    }

    override suspend fun update(category: Category) {
        flow.value = flow.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: CategoryId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

internal class FakeCurrencyRepository(
    private val currencies: List<Currency> = emptyList(),
) : CurrencyRepository {
    override fun getAll(): Flow<List<Currency>> = flowOf(currencies)
}

/**
 * Accepts the cross-device registration calls without recording them: which refs get written is
 * `CreateWorkspaceUseCase`'s contract and is asserted in its own spec, not through the screen.
 */
internal class RecordingUserRemoteRepository : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = error(UNUSED)
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error(UNUSED)

    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun findByEmail(email: String): UserId? = error(UNUSED)
    override suspend fun upsertEmailMapping(email: String, uid: String) = error(UNUSED)
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error(UNUSED)
}

/**
 * [pushed] is what `pushAll()` reports: `false` stands for "sync is switched off", which the
 * create flow treats as a success that simply registers no remote refs. [failPush] is the
 * transport blowing up, which it must surface to the user instead.
 */
internal class FakeWorkspaceSyncer(
    private val pushed: Boolean = true,
    private val failPush: Boolean = false,
) : WorkspaceSyncer {
    override suspend fun pushAll(): Boolean {
        if (failPush) error("simulated remote push failure")
        return pushed
    }

    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

internal class RecordingAuthRemoteRepository : AuthRemoteRepository {
    var signOuts = 0
        private set

    override fun currentUid(): String? = null
    override fun currentEmail(): String? = null
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun createUserWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun signInAnonymously() = error(UNUSED)

    override suspend fun signOut(): Either<AuthError, Unit> {
        signOuts++
        return Unit.right()
    }

    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}

internal class RecordingLocalDataResetRepository : LocalDataResetRepository {
    var clears = 0
        private set

    override suspend fun clearAll() {
        clears++
    }
}

internal class RecordingRemoteDataResetRepository : RemoteDataResetRepository {
    var clears = 0
        private set

    override suspend fun clearAll() {
        clears++
    }
}

internal class RecordingSessionShutdownGate : SessionShutdownGate {
    override suspend fun shutdown() = Unit
}

internal fun aCurrency(code: CurrencyCode, symbol: String = "$", displayName: String = code.value): Currency =
    Currency(code = code, symbol = symbol, displayName = displayName)
