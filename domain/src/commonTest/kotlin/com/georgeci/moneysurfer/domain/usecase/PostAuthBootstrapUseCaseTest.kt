package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

class PostAuthBootstrapUseCaseTest : StringSpec({

    "first-time user creates remote doc and returns FirstTime" {
        val recording = RecordingRemoteRepo(returnUser = null)
        val env = BootstrapEnv(remote = recording)

        val result = env.useCase(uid = UID, email = "x@y", displayName = "X", isAnon = false)

        result.shouldBeInstanceOf<Either.Right<PostAuthBootstrapUseCase.Result>>()
        result.value shouldBe PostAuthBootstrapUseCase.Result.FirstTime
        recording.createCalls.size shouldBe 1
        env.session.currentWorkspaceId.flow.first() shouldBe null
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
        env.session.currentWorkspaceId.flow.first() shouldBe WS_2
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
        env.session.currentWorkspaceId.flow.first() shouldBe WS_1
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
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }

    "fetch failure propagates as AuthError.Unknown" {
        val env = BootstrapEnv(remote = FailingFetchRepo())

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        result.shouldBeInstanceOf<Either.Left<*>>()
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }

    "workspace pull failure aborts bootstrap with AuthError" {
        val env = BootstrapEnv(
            remoteUser = aUser(workspaceIds = listOf(WS_1, WS_2), defaultWorkspaceId = WS_1),
            failOnSync = true,
        )

        val result = env.useCase(uid = UID, email = null, displayName = null, isAnon = false)

        val left = result.shouldBeInstanceOf<Either.Left<com.georgeci.moneysurfer.domain.auth.AuthError>>()
        // Local pointer must NOT be seeded — the bootstrap raised before reaching the seed step.
        env.session.currentWorkspaceId.flow.first() shouldBe null
        // Sync was attempted.
        env.syncer.syncAllCount shouldBe 1
        // Cause is the simulated Firestore exception.
        left.value.cause shouldNotBe null
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
) {
    constructor(remoteUser: User?, failOnSync: Boolean = false) : this(
        remote = RecordingRemoteRepo(returnUser = remoteUser),
        syncer = BootstrapWorkspaceSyncer(failOnSync = failOnSync),
    )

    val session = InMemorySessionPointers()
    val useCase = PostAuthBootstrapUseCase(
        userRemoteRepository = remote,
        workspaceSyncer = syncer,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
}

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
    override suspend fun pushAll() = Unit
    override suspend fun syncAll() {
        syncAllCount++
        if (failOnSync) throw RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions")
    }
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
