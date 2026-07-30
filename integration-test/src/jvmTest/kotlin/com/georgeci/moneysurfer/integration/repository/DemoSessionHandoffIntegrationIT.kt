package com.georgeci.moneysurfer.integration.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import arrow.core.Either
import arrow.core.right
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.SessionConfigOverlayImpl
import com.georgeci.moneysurfer.data.config.LocalConfigSourceImpl
import com.georgeci.moneysurfer.data.config.SyncedSettingsSessionImpl
import com.georgeci.moneysurfer.data.preferences.SessionPointersImpl
import com.georgeci.moneysurfer.data.repository.CategoryRepositoryImpl
import com.georgeci.moneysurfer.data.repository.LocalDataResetRepositoryImpl
import com.georgeci.moneysurfer.data.repository.TimeFormatter
import com.georgeci.moneysurfer.data.repository.UserRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceMemberRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceRepositoryImpl
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.constants.PREFILLED_DEFAULT_USER_ID
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.usecase.AbandonAuthSessionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.LoginUseCase
import com.georgeci.moneysurfer.domain.usecase.LogoutUseCase
import com.georgeci.moneysurfer.domain.usecase.PostAuthBootstrapUseCase
import com.georgeci.moneysurfer.domain.usecase.WipeDemoDataUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import java.nio.file.Files

private const val REAL_UID = "uid-real-owner"
private const val EMAIL = "e2e+owner@test.local"
private const val PASSWORD = "secret1"
private const val UNUSED = "collaborator not exercised by this test"

/**
 * Demo session → logout → email sign-in, against the real DataStore-backed [SessionPointersImpl]
 * and a real database.
 *
 * `LogoutUseCase` deliberately leaves `hasUsedDemo` set — it is device-scoped, and the next real
 * sign-in is what consumes it — so the sign-in after a demo session is the one run where
 * `WipeDemoDataUseCase` fires *and* the session pointers are pinned. Everything either use case
 * touches lives in the same two stores, which is why this is worth asserting end to end rather
 * than against `InMemorySessionPointers`: the wipe must not take the new session's pointers with
 * it, and the pointers must be on disk by the time the flow returns, or the next cold start
 * resolves a start route with no user and lands back on sign-in.
 */
class DemoSessionHandoffIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: AuthStack

    // Stands in for the application scope the graph binds — the config layer keeps one collection
    // of the preferences store on it for as long as the source lives.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    afterSpec { scope.cancel() }

    beforeEach {
        harness = IntegrationHarness()
        stack = AuthStack(harness, scope)
    }
    afterEach {
        harness.close()
        stack.closeStore()
    }

    "a real sign-in after a demo session keeps its pointers, and the demo flag is spent" {
        stack.demoLogin().shouldBeInstanceOf<Either.Right<Unit>>()
        stack.createWorkspace("Demo budget")
        stack.session.hasUsedDemo.first() shouldBe true
        stack.logout()

        val result = stack.login()

        result.shouldBeInstanceOf<Either.Right<PostAuthBootstrapUseCase.Result>>()
        stack.session.currentUserId.first() shouldBe UserId(REAL_UID)
        stack.session.currentFirebaseUid.first() shouldBe REAL_UID
        stack.session.hasUsedDemo.first() shouldBe false
    }

    "the workspace created after that sign-in stays pinned" {
        // `PostAuthBootstrapUseCase` returns FirstTime for an account with no `users/{uid}` doc, so
        // nothing pins a workspace until the selector creates one — the last write of the flow, and
        // the one the bug report saw logged as done with nothing behind it.
        stack.demoLogin()
        stack.createWorkspace("Demo budget")
        stack.logout()
        stack.login()

        val created = stack.createWorkspace("Real budget")

        stack.session.currentWorkspaceId.first() shouldBe created
    }

    "the pointers are on disk, so the next cold start does not route back to sign in" {
        stack.demoLogin()
        stack.createWorkspace("Demo budget")
        stack.logout()
        stack.login()
        val created = stack.createWorkspace("Real budget")

        val restarted = stack.reopenSession()

        restarted.currentUserId.first() shouldBe UserId(REAL_UID)
        restarted.currentFirebaseUid.first() shouldBe REAL_UID
        restarted.currentWorkspaceId.first() shouldBe created
    }

    "the demo user's local rows are gone, but the signed-in user's row is not" {
        // The other half of the wipe's contract, and the reason its position in the flow is not
        // free: it has to run before `AuthLocalRepository` writes the real `users` row, or it
        // deletes the row `workspaces.ownerId` needs and the account starts out unable to create
        // a workspace. Asserted together so a fix for either direction cannot be "stop wiping" or
        // "wipe later".
        stack.demoLogin()
        stack.createWorkspace("Demo budget")
        stack.logout()

        stack.login()

        harness.database.userDao().getById(PREFILLED_DEFAULT_USER_ID) shouldBe null
        stack.workspaceRepository.getAll().first() shouldBe emptyList()
        harness.database.userDao().getById(REAL_UID)?.id shouldBe REAL_UID
    }
})

/**
 * The session-lifecycle use cases wired over real `:data-local` implementations — the session
 * pointers and the config layer share one DataStore file, as they do in the graph.
 *
 * The DataStore is built with a scope this class owns rather than through `createDataStore`, so
 * [reopenSession] can release the file's lock and read it back the way a cold start would.
 */
private class AuthStack(harness: IntegrationHarness, scope: CoroutineScope) {

    private val path = Files.createTempDirectory("ms-demo-handoff")
        .resolve("moneysurfer_settings.preferences_pb")
        .toString()

    /**
     * Every store this class has opened on [path], newest last. DataStore holds a per-file lock for
     * the lifetime of its scope and offers no close of its own, so the jobs have to be kept to be
     * released — [reopenSession] would otherwise strand one lock per call.
     */
    private val storeJobs = mutableListOf<Job>()
    private val store = openStore()

    val session = SessionPointersImpl(store)

    private val clock = ClockUseCase()
    private val timeFormatter = TimeFormatter()
    private val outbox = SessionOnlyOutbox
    private val overlay = SessionConfigOverlayImpl()

    val workspaceRepository = WorkspaceRepositoryImpl(
        dao = harness.database.workspaceDao(),
        outboxEnqueuer = outbox,
        clock = clock,
        timeFormatter = timeFormatter,
    )

    private val localConfig = LocalConfigSourceImpl(
        store,
        scope,
        harness.database.configEntryDao(),
        clock,
        outbox,
    )
    private val localReset = LocalDataResetRepositoryImpl(
        harness.database,
        localConfig,
        overlay,
        emptyList<ConfigKeyGroup>(),
    )
    private val syncedSettings = SyncedSettingsSessionImpl(
        overlay,
        harness.database.configEntryDao(),
        outbox,
    )

    private val userRepository = UserRepositoryImpl(harness.database.userDao())
    private val authLocal = AuthLocalRepository(userRepository, session)
    private val userRemote = FirstTimeUserRemoteRepo()

    private val demoLoginUseCase = DemoLoginUseCase(authLocal, session, syncedSettings)

    private val logoutUseCase = LogoutUseCase(
        sessionShutdownGate = NoOpShutdownGate,
        authRemoteRepository = StubAuthRepo,
        localDataResetRepository = localReset,
        remoteDataResetRepository = NoOpRemoteReset,
        sessionMutator = session,
    )

    private val loginUseCase = LoginUseCase(
        authRemoteRepository = StubAuthRepo,
        authLocalRepository = authLocal,
        sessionMutator = session,
        wipeDemoDataUseCase = WipeDemoDataUseCase(localReset, session, session),
        postAuthBootstrap = PostAuthBootstrapUseCase(
            userRemoteRepository = userRemote,
            workspaceRepository = workspaceRepository,
            workspaceSyncer = NoOpSyncer,
            sessionMutator = session,
            getCurrentTime = GetCurrentTimeUseCase(clock),
            syncedSettingsSession = syncedSettings,
        ),
        abandonAuthSession = AbandonAuthSessionUseCase(session, StubAuthRepo),
    )

    private val createWorkspaceUseCase = CreateWorkspaceUseCase(
        workspaceRepository = workspaceRepository,
        workspaceMemberRepository = WorkspaceMemberRepositoryImpl(
            dao = harness.database.workspaceMemberDao(),
            outboxEnqueuer = outbox,
            clock = clock,
            timeFormatter = timeFormatter,
        ),
        categoryRepository = CategoryRepositoryImpl(
            dao = harness.database.categoryDao(),
            outboxEnqueuer = outbox,
            clock = clock,
            timeFormatter = timeFormatter,
        ),
        userRemoteRepository = userRemote,
        workspaceSyncer = NoOpSyncer,
        session = session,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(clock),
    )

    suspend fun demoLogin(): Either<AuthError, Unit> = demoLoginUseCase()

    suspend fun logout() = logoutUseCase()

    suspend fun login(): Either<AuthError, PostAuthBootstrapUseCase.Result> =
        loginUseCase(EMAIL, PASSWORD)

    suspend fun createWorkspace(name: String): WorkspaceId {
        val result = createWorkspaceUseCase(
            CreateWorkspaceUseCase.Params(
                name = name,
                description = "",
                baseCurrency = CurrencyCode("USD"),
            ),
        )
        return result.getOrNull() ?: error("create workspace failed: $result")
    }

    /**
     * Drops the live DataStore and opens the same file again — the process restart the bug report
     * ends on. The old scope has to finish completing before the new store can take the file lock,
     * hence the join.
     */
    suspend fun reopenSession(): SessionPointersImpl {
        storeJobs.last().cancelAndJoin()
        return SessionPointersImpl(openStore())
    }

    fun closeStore() {
        storeJobs.forEach { it.cancel() }
    }

    private fun openStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        storeJobs += job
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { path.toPath() },
        )
    }
}

private object StubAuthRepo : AuthRemoteRepository {
    override fun currentUid(): String? = REAL_UID
    override fun currentEmail(): String = EMAIL
    override fun isCurrentUserAnonymous(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = REAL_UID.right()
    override suspend fun createUserWithEmail(email: String, password: String) = REAL_UID.right()
    override suspend fun signInAnonymously() = REAL_UID.right()
    override suspend fun signOut() = Unit.right()
    override suspend fun reauthenticateWithEmail(email: String, password: String) = error(UNUSED)
    override suspend fun deleteCurrentUser() = error(UNUSED)
}

/** No `users/{uid}` document yet, so the bootstrap creates one and reports `FirstTime`. */
private class FirstTimeUserRemoteRepo : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = Unit
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun findByEmail(email: String): UserId? = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private object NoOpSyncer : WorkspaceSyncer {
    override suspend fun pushAll(): Boolean = true
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

private object NoOpShutdownGate : SessionShutdownGate {
    override suspend fun shutdown() = Unit
}

private object NoOpRemoteReset : RemoteDataResetRepository {
    override suspend fun clearAll() = Unit
}

private object SessionOnlyOutbox : OutboxEnqueuer {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) = Unit

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) = Unit

    override suspend fun isEnabled(): Boolean = false
}
