package com.georgeci.moneysurfer.integration.sync

import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.repository.CategoryRepositoryImpl
import com.georgeci.moneysurfer.data.repository.TimeFormatter
import com.georgeci.moneysurfer.data.repository.WorkspaceMemberRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceRepositoryImpl
import com.georgeci.moneysurfer.data.sync.CurrentAuthInfo
import com.georgeci.moneysurfer.data.sync.UploadPendingChangesUseCaseImpl
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.internal.repository.PendingMutationQueueImpl
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuerImpl
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * End-to-end "create workspace → outbox → drain" over the real Room schema:
 * `CreateWorkspaceUseCase` writes through the real repository impls whose
 * dual-writes land in the real `PendingMutationQueueImpl`, and
 * `UploadPendingChangesUseCaseImpl` drains that queue into recording plugins.
 *
 * Firestore itself is faked (gitlive's JVM artifact cannot run on plain JVM —
 * see [IntegrationHarness]); the wire format is covered by `androidDeviceTest`.
 */
class WorkspaceCreationSyncIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: CreationStack

    beforeEach {
        harness = IntegrationHarness()
        stack = CreationStack(harness, firebaseUid = FIREBASE_UID)
        stack.seedOwner()
    }

    afterEach {
        harness.close()
    }

    "create workspace persists local rows and enqueues one outbox mutation per row" {
        val result = stack.createWorkspace()

        val newId = result.shouldBeInstanceOf<Either.Right<WorkspaceId>>().value

        // Room is local truth — all rows land regardless of what happens remotely.
        harness.database.workspaceDao().getById(newId.value).shouldNotBeNull().name shouldBe "Family"
        stack.memberRepository.getByWorkspaceId(newId).first() shouldHaveSize 1
        stack.categoryRepository.getByWorkspaceId(newId).first() shouldHaveSize
            DEFAULT_CATEGORY_SEEDS.size

        // Dual-write: every inserted row has exactly one INSERT mutation in the outbox.
        val pending = stack.queue.pending(SyncScope.AllUserData)
        pending shouldHaveSize 2 + DEFAULT_CATEGORY_SEEDS.size
        pending.groupingBy { it.entityType }.eachCount() shouldBe mapOf(
            SyncEntityTypes.WORKSPACE to 1,
            SyncEntityTypes.WORKSPACE_MEMBER to 1,
            SyncEntityTypes.CATEGORY to DEFAULT_CATEGORY_SEEDS.size,
        )
        pending.map { it.operation }.toSet() shouldBe setOf(MutationOperation.INSERT)
        pending.map { it.scopeKey }.toSet() shouldBe setOf(newId.value)
    }

    "drain pushes every queued mutation through its plugin and empties the outbox" {
        stack.createWorkspace().shouldBeInstanceOf<Either.Right<WorkspaceId>>()
        val plugins = listOf(
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE),
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE_MEMBER),
            RecordingPushPlugin(SyncEntityTypes.CATEGORY),
        )
        val drain = stack.uploadUseCase(plugins)

        val result = drain(SyncScope.UploadOnly, {}, SimpleCancelToken())

        val expectedTotal = 2 + DEFAULT_CATEGORY_SEEDS.size
        result.shouldBeInstanceOf<Either.Right<*>>()
        plugins.sumOf { it.pushed.size } shouldBe expectedTotal
        plugins.single { it.entityType == SyncEntityTypes.WORKSPACE }.pushed shouldHaveSize 1
        plugins.single { it.entityType == SyncEntityTypes.CATEGORY }.pushed shouldHaveSize
            DEFAULT_CATEGORY_SEEDS.size
        stack.queue.pending(SyncScope.AllUserData).shouldBeEmpty()
    }

    "failed push keeps the mutation pending with bumped attempts and a retry drains it" {
        stack.createWorkspace().shouldBeInstanceOf<Either.Right<WorkspaceId>>()
        val total = 2 + DEFAULT_CATEGORY_SEEDS.size
        val failing = listOf(
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE),
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE_MEMBER, failAlways = true),
            RecordingPushPlugin(SyncEntityTypes.CATEGORY),
        )

        val firstRun = stack.uploadUseCase(failing)(SyncScope.UploadOnly, {}, SimpleCancelToken())

        firstRun.shouldBeInstanceOf<Either.Left<*>>()
        // WORKSPACE pushed first (creation order) and is gone; the member failed and
        // everything after it returned to PENDING with attempts bumped — durable in Room.
        val remaining = stack.queue.pending(SyncScope.AllUserData)
        remaining shouldHaveSize total - 1
        remaining.map { it.attempts }.toSet() shouldBe setOf(1)

        val healthy = listOf(
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE),
            RecordingPushPlugin(SyncEntityTypes.WORKSPACE_MEMBER),
            RecordingPushPlugin(SyncEntityTypes.CATEGORY),
        )
        val secondRun = stack.uploadUseCase(healthy)(SyncScope.UploadOnly, {}, SimpleCancelToken())

        secondRun.shouldBeInstanceOf<Either.Right<*>>()
        stack.queue.pending(SyncScope.AllUserData).shouldBeEmpty()
    }

    "demo session (no Firebase uid) writes local rows but keeps the outbox empty" {
        // Owner row is already seeded by beforeEach; only the session differs.
        val demoStack = CreationStack(harness, firebaseUid = null)

        val result = demoStack.createWorkspace()

        val newId = result.shouldBeInstanceOf<Either.Right<WorkspaceId>>().value
        harness.database.workspaceDao().getById(newId.value).shouldNotBeNull()
        demoStack.queue.pending(SyncScope.AllUserData).shouldBeEmpty()
    }
})

private const val OWNER_UID = "owner-uid"
private const val FIREBASE_UID = "firebase-uid-1"

/**
 * Real Room + real outbox around [CreateWorkspaceUseCase]; only the Firestore-facing
 * collaborators ([WorkspaceSyncer], [UserRemoteRepository]) are no-op fakes.
 */
private class CreationStack(
    private val harness: IntegrationHarness,
    firebaseUid: String?,
) {
    val session = InMemorySessionPointers(
        currentUserId = UserId(OWNER_UID),
        currentFirebaseUid = firebaseUid,
    )
    val queue = PendingMutationQueueImpl(harness.syncDatabase.pendingMutationDao())

    private val enqueuer = OutboxEnqueuerImpl(
        outbox = queue,
        session = session,
        appVersionGate = AlwaysAllowedVersionGate,
    )
    private val clock = ClockUseCase()
    private val timeFormatter = TimeFormatter()

    private val workspaceRepository = WorkspaceRepositoryImpl(
        dao = harness.database.workspaceDao(),
        outboxEnqueuer = enqueuer,
        clock = clock,
        timeFormatter = timeFormatter,
    )
    val memberRepository = WorkspaceMemberRepositoryImpl(
        dao = harness.database.workspaceMemberDao(),
        outboxEnqueuer = enqueuer,
        clock = clock,
        timeFormatter = timeFormatter,
    )
    val categoryRepository = CategoryRepositoryImpl(
        dao = harness.database.categoryDao(),
        outboxEnqueuer = enqueuer,
        clock = clock,
        timeFormatter = timeFormatter,
    )

    private val createWorkspaceUseCase = CreateWorkspaceUseCase(
        workspaceRepository = workspaceRepository,
        workspaceMemberRepository = memberRepository,
        categoryRepository = categoryRepository,
        userRemoteRepository = NoOpUserRemoteRepository,
        workspaceSyncer = NoOpWorkspaceSyncer,
        session = session,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(clock),
    )

    suspend fun seedOwner() {
        harness.database.userDao().insert(
            UserEntity(id = OWNER_UID, displayName = "Owner", isAnon = false),
        )
    }

    suspend fun createWorkspace() = createWorkspaceUseCase(
        CreateWorkspaceUseCase.Params(
            name = "Family",
            description = "Shared budget",
            baseCurrency = com.georgeci.moneysurfer.domain.primitives.CurrencyCode("USD"),
        ),
    )

    fun uploadUseCase(plugins: List<SyncEntityPlugin>) = UploadPendingChangesUseCaseImpl(
        outbox = queue,
        authInfo = FakeAuthInfo,
        plugins = plugins,
    )
}

private object AlwaysAllowedVersionGate : AppVersionGate {
    override val status: StateFlow<AppVersionStatus?> =
        MutableStateFlow(AppVersionStatus.Supported)

    override suspend fun refresh(): AppVersionStatus = AppVersionStatus.Supported
    override fun isSyncAllowed(): Boolean = true
}

private object FakeAuthInfo : CurrentAuthInfo {
    override fun diagnostics(): String = "integration-test"
}

private object NoOpWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll() = Unit
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

private object NoOpUserRemoteRepository : UserRemoteRepository {
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
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private class RecordingPushPlugin(
    override val entityType: String,
    private val failAlways: Boolean = false,
) : SyncEntityPlugin {
    override val firestoreCollectionName: String? = null

    val pushed = mutableListOf<PendingMutation>()

    override suspend fun push(mutation: PendingMutation) {
        if (failAlways) error("simulated push failure for $entityType")
        pushed += mutation
    }

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult =
        error("applyDoc is not part of the upload path")
}
