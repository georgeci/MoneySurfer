package com.georgeci.moneysurfer.feature.settings

import arrow.core.Either
import arrow.core.right
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeSyncSettings
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.UnavailableDebugConfigInspector
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.usecase.ListPendingInvitesForCurrentUserUseCase
import com.georgeci.moneysurfer.domain.usecase.LogoutUseCase
import com.georgeci.moneysurfer.domain.usecase.RefreshIncomingInvitesUseCase
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Guest-vs-real logout branch in [SettingsViewModel]: a guest (anonymous) session is local-only,
 * so logging out is destructive and must be confirmed first; a real user keeps their data in the
 * cloud, so their logout runs immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLogoutViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "guest logout asks for confirmation before wiping data" {
        val env = VmEnv(isAnonymous = true)

        env.viewModel.onEvent(SettingsEvent.OnLogoutClick)

        env.viewModel.currentState.showGuestLogoutWarning shouldBe true
        env.auth.signedOut shouldBe false
    }

    "dismissing the guest warning cancels the logout" {
        val env = VmEnv(isAnonymous = true)

        env.viewModel.onEvent(SettingsEvent.OnLogoutClick)
        env.viewModel.onEvent(SettingsEvent.OnGuestLogoutDismissed)

        env.viewModel.currentState.showGuestLogoutWarning shouldBe false
        env.auth.signedOut shouldBe false
    }

    "confirming the guest warning logs out and closes the dialog" {
        val env = VmEnv(isAnonymous = true)

        env.viewModel.onEvent(SettingsEvent.OnLogoutClick)
        env.viewModel.onEvent(SettingsEvent.OnGuestLogoutConfirmed)

        env.viewModel.currentState.showGuestLogoutWarning shouldBe false
        env.auth.signedOut shouldBe true
    }

    "a real user logs out immediately without a warning" {
        val env = VmEnv(isAnonymous = false)

        env.viewModel.onEvent(SettingsEvent.OnLogoutClick)

        env.viewModel.currentState.showGuestLogoutWarning shouldBe false
        env.auth.signedOut shouldBe true
    }
})

private class VmEnv(isAnonymous: Boolean) {
    val auth = RecordingAuth(isAnonymous)
    private val session = InMemorySessionPointers()

    private val logoutUseCase = LogoutUseCase(
        sessionShutdownGate = object : SessionShutdownGate {
            override suspend fun shutdown() = Unit
        },
        authRemoteRepository = auth,
        localDataResetRepository = object : LocalDataResetRepository {
            override suspend fun clearAll() = Unit
        },
        remoteDataResetRepository = object : RemoteDataResetRepository {
            override suspend fun clearAll() = Unit
        },
        sessionMutator = session,
    )

    val viewModel = SettingsViewModel(
        session = session,
        authRemoteRepository = auth,
        pendingMutationQueue = StubPendingMutationQueue,
        logoutUseCase = logoutUseCase,
        listPendingInvites = ListPendingInvitesForCurrentUserUseCase(
            StubInviteRepository,
            StubUserRepository,
            session,
        ),
        refreshIncomingInvites = RefreshIncomingInvitesUseCase(StubWorkspaceSyncer),
        memberRepository = StubMemberRepository,
        uiPreferences = FakeUiPreferences(),
        syncSettings = FakeSyncSettings(enabled = false),
        appInfo = AppInfo(version = "1.0.0", versionCode = 1),
        hostCapabilities = FakeHostCapabilities(),
        debugConfigInspector = UnavailableDebugConfigInspector,
    )
}

private class RecordingAuth(private val anonymous: Boolean) : AuthRemoteRepository {
    var signedOut = false
        private set

    override fun currentUid(): String? = "uid-1"
    override fun currentEmail(): String? = if (anonymous) null else "user@example.com"
    override fun isCurrentUserAnonymous(): Boolean = anonymous
    override suspend fun signInWithEmail(email: String, password: String) = error("unused")
    override suspend fun createUserWithEmail(email: String, password: String) = error("unused")
    override suspend fun signInAnonymously() = error("unused")
    override suspend fun signOut(): Either<AuthError, Unit> {
        signedOut = true
        return Unit.right()
    }
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error("unused")
    override suspend fun deleteCurrentUser() = error("unused")
}

private object StubPendingMutationQueue : PendingMutationQueue {
    override suspend fun enqueue(mutation: PendingMutation) = error("unused")
    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> = error("unused")
    override suspend fun markInFlight(ids: List<String>) = error("unused")
    override suspend fun markCompleted(ids: List<String>) = error("unused")
    override suspend fun markFailed(id: String, error: String) = error("unused")
    override val pendingCount: Flow<Int> = flowOf(0)
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
}

private object StubUserRepository : UserRepository {
    override fun getAll() = error("unused")
    override suspend fun getById(id: UserId): User? = null
    override suspend fun insert(user: User) = error("unused")
    override suspend fun update(user: User) = error("unused")
    override suspend fun upsert(user: User) = error("unused")
    override suspend fun delete(id: UserId) = error("unused")
}

private object StubInviteRepository : WorkspaceInviteRepository {
    override fun getAll() = error("unused")
    override fun getByWorkspaceId(workspaceId: WorkspaceId) = error("unused")
    override fun getByEmail(email: String) = error("unused")
    override fun getByTargetUserId(userId: UserId): Flow<List<WorkspaceInvite>> = flowOf(emptyList())
    override suspend fun getById(id: WorkspaceInviteId): WorkspaceInvite? = null
    override suspend fun upsert(invite: WorkspaceInvite) = error("unused")
    override suspend fun cancel(id: WorkspaceInviteId) = error("unused")
    override suspend fun markAccepted(id: WorkspaceInviteId) = error("unused")
    override suspend fun markDeclined(id: WorkspaceInviteId) = error("unused")
}

private object StubMemberRepository : WorkspaceMemberRepository {
    override fun getAll() = error("unused")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId) = error("unused")
    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? = null
    override suspend fun insert(member: WorkspaceMember) = error("unused")
    override suspend fun update(member: WorkspaceMember) = error("unused")
    override suspend fun markRemoved(
        userId: UserId,
        workspaceId: WorkspaceId,
        removedByUserId: UserId?,
    ) = error("unused")

    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) = error("unused")
}

private object StubWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll(): Boolean = true
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
