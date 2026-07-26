package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class PostAuthBootstrapUseCaseTest : StringSpec({

    "first-time user creates remote doc and returns FirstTime" {
        val recording = RecordingRemoteRepo(returnUser = null)
        val env = BootstrapEnv(remote = recording)

        val result = env.useCase(uid = UID, email = "x@y", displayName = "X", isAnon = false)

        result.shouldBeInstanceOf<Either.Right<PostAuthBootstrapUseCase.Result>>()
        result.value shouldBe PostAuthBootstrapUseCase.Result.FirstTime
        recording.createCalls.size shouldBe 1
        env.session.currentWorkspaceId.first() shouldBe null
    }

    "existing user with explicit defaultWorkspaceId is preserved" {
        val env = BootstrapEnv(
            remoteUser = aUser(
                workspaceIds = listOf(WS_1, WS_2),
                defaultWorkspaceId = WS_2,
            ),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val existing = (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.ExistingUser>()
        existing.defaultWorkspaceId shouldBe WS_2
        existing.workspaceIds shouldBe listOf(WS_1, WS_2)
        env.session.currentWorkspaceId.first() shouldBe WS_2
    }

    "existing user with null defaultWorkspaceId falls back to the first workspace" {
        val env = BootstrapEnv(
            remoteUser = aUser(
                workspaceIds = listOf(WS_1, WS_2),
                defaultWorkspaceId = null,
            ),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val existing = (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.ExistingUser>()
        existing.defaultWorkspaceId shouldBe WS_1
        env.session.currentWorkspaceId.first() shouldBe WS_1
    }

    "existing user with empty workspaceIds returns null default and leaves pointer untouched" {
        val env = BootstrapEnv(
            remoteUser = aUser(
                workspaceIds = emptyList(),
                defaultWorkspaceId = null,
            ),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val existing = (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.ExistingUser>()
        existing.defaultWorkspaceId shouldBe null
        existing.workspaceIds shouldBe emptyList()
        env.session.currentWorkspaceId.first() shouldBe null
    }

    "fetch failure propagates as AuthError.Unknown" {
        val env = BootstrapEnv(remote = FailingFetchRepo())

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        result.shouldBeInstanceOf<Either.Left<*>>()
        env.session.currentWorkspaceId.first() shouldBe null
    }

    "workspace pull failure aborts bootstrap with AuthError" {
        val env = BootstrapEnv(
            remoteUser = aUser(workspaceIds = listOf(WS_1, WS_2), defaultWorkspaceId = WS_1),
            failOnSync = true,
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val left = result.shouldBeInstanceOf<Either.Left<com.georgeci.moneysurfer.domain.auth.AuthError>>()
        // Local pointer must NOT be seeded — the bootstrap raised before reaching the seed step.
        env.session.currentWorkspaceId.first() shouldBe null
        // Sync was attempted.
        env.syncer.syncAllCount shouldBe 1
        // Cause is the simulated Firestore exception.
        left.value.cause shouldNotBe null
    }

    // -- Hydration guard (issue #342) --------------------------------------------------------
    // Pinning a workspace the pull never brought down routes the next cold start to Dashboard on
    // top of an empty database, and the selector is never shown again.

    "workspace the pull did not hydrate is never pinned" {
        val env = BootstrapEnv(
            remoteUser = aUser(workspaceIds = listOf(WS_1), defaultWorkspaceId = WS_1),
            hydratedWorkspaceIds = emptyList(),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.CloudDataUnavailable>()
            .workspaceIds shouldBe listOf(WS_1)
        env.session.currentWorkspaceId.first() shouldBe null
    }

    "defaultWorkspaceId that did not hydrate falls back to one that did" {
        val env = BootstrapEnv(
            remoteUser = aUser(workspaceIds = listOf(WS_1, WS_2), defaultWorkspaceId = WS_1),
            hydratedWorkspaceIds = listOf(WS_2),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val existing = (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.ExistingUser>()
        existing.defaultWorkspaceId shouldBe WS_2
        env.session.currentWorkspaceId.first() shouldBe WS_2
    }

    "a default pointing outside workspaceIds is not pinned just because it is the server's choice" {
        // Server-side skew: `defaultWorkspaceId` is not validated against `workspaceIds`, so the
        // pull never visits WS_2 and it cannot be the active workspace.
        val env = BootstrapEnv(
            remoteUser = aUser(workspaceIds = listOf(WS_1), defaultWorkspaceId = WS_2),
            hydratedWorkspaceIds = listOf(WS_1),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val existing = (result as Either.Right).value
            .shouldBeInstanceOf<PostAuthBootstrapUseCase.Result.ExistingUser>()
        existing.defaultWorkspaceId shouldBe WS_1
        env.session.currentWorkspaceId.first() shouldBe WS_1
    }

    "permission-denied during fetch maps to AuthError.PermissionDenied" {
        val env = BootstrapEnv(
            remote = FailingFetchRepo(
                exception = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions"),
            ),
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val left = result.shouldBeInstanceOf<Either.Left<com.georgeci.moneysurfer.domain.auth.AuthError>>()
        left.value.type shouldBe com.georgeci.moneysurfer.domain.auth.AuthError.Type.PermissionDenied
    }
})

private const val UID = "uid-1"
private val WS_1 = WorkspaceId("ws-1")
private val WS_2 = WorkspaceId("ws-2")

private fun aUser(
    workspaceIds: List<WorkspaceId>,
    defaultWorkspaceId: WorkspaceId?,
): User = User(
    id = UserId(UID),
    displayName = null,
    email = null,
    isAnon = false,
    workspaceIds = workspaceIds,
    defaultWorkspaceId = defaultWorkspaceId,
)

private class BootstrapEnv(
    val remote: UserRemoteRepository,
    val syncer: BootstrapWorkspaceSyncer = BootstrapWorkspaceSyncer(),
    hydratedWorkspaceIds: List<WorkspaceId> = emptyList(),
) {
    /**
     * [hydratedWorkspaceIds] stands in for what the pull left in Room. It defaults to
     * [remoteUser]'s whole list, so a test only spells it out when it cares about the gap
     * between "the remote says the user owns it" and "it is actually here".
     */
    constructor(
        remoteUser: User?,
        failOnSync: Boolean = false,
        hydratedWorkspaceIds: List<WorkspaceId> = remoteUser?.workspaceIds.orEmpty(),
    ) : this(
        remote = RecordingRemoteRepo(returnUser = remoteUser),
        syncer = BootstrapWorkspaceSyncer(failOnSync = failOnSync),
        hydratedWorkspaceIds = hydratedWorkspaceIds,
    )

    val session = InMemorySessionPointers()
    val useCase = PostAuthBootstrapUseCase(
        userRemoteRepository = remote,
        workspaceRepository = LocalWorkspaces(hydratedWorkspaceIds),
        workspaceSyncer = syncer,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
}

/** Room stand-in: only [getById] matters, and only for the ids the pull hydrated. */
private class LocalWorkspaces(
    private val hydrated: List<WorkspaceId>,
) : WorkspaceRepository {
    override fun getAll(): Flow<List<Workspace>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(emptyList())
    override suspend fun getById(id: WorkspaceId): Workspace? =
        if (id in hydrated) aWorkspace(id) else null
    override suspend fun insert(workspace: Workspace) = error("not used")
    override suspend fun update(workspace: Workspace) = error("not used")
    override suspend fun delete(id: WorkspaceId) = error("not used")
}

private fun aWorkspace(id: WorkspaceId) = Workspace(
    id = id,
    name = id.value,
    description = "",
    baseCurrency = CurrencyCode("EUR"),
    ownerId = UserId(UID),
    createdAt = Instant.fromEpochMilliseconds(0),
)

private class RecordingRemoteRepo(
    private val returnUser: User?,
) : UserRemoteRepository {
    val createCalls = mutableListOf<String>()
    override suspend fun fetch(uid: String): User? = returnUser
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) {
        createCalls += uid
    }
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private class FailingFetchRepo(
    private val exception: Throwable = RuntimeException("fetch failed"),
) : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = throw exception
    override suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    ) = error("not used")
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = error("not used")
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private class BootstrapWorkspaceSyncer(
    private val failOnSync: Boolean = false,
) : WorkspaceSyncer {
    var syncAllCount = 0
    override suspend fun pushAll(): Boolean = true
    override suspend fun syncAll() {
        syncAllCount++
        if (failOnSync) throw RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions")
    }
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
