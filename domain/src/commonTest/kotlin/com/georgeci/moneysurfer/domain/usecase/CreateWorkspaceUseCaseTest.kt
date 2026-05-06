package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock as KotlinClock

class CreateWorkspaceUseCaseTest : StringSpec({

    "returns NoCurrentUser error when no current user" {
        val env = TestEnv(currentUserId = null)
        val result = env.useCase(defaultParams())

        result.shouldBeInstanceOf<Either.Left<CreateWorkspaceError>>()
        result.value shouldBe CreateWorkspaceError.NoCurrentUser
        env.workspaceRepo.inserted shouldHaveSize 0
        env.memberRepo.inserted shouldHaveSize 0
        env.categoryRepo.inserted shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }

    "writes workspace, member, default categories and pins it as current (no Firebase session)" {
        val env = TestEnv(currentUserId = OWNER_ID, firebaseUid = null)
        val before = KotlinClock.System.now()

        val result = env.useCase(defaultParams())

        val after = KotlinClock.System.now()
        result.shouldBeInstanceOf<Either.Right<WorkspaceId>>()
        val newId = result.value

        env.workspaceRepo.inserted shouldHaveSize 1
        val ws = env.workspaceRepo.inserted.single()
        ws.id shouldBe newId
        ws.name shouldBe "My Budget"
        ws.description shouldBe "Personal expenses"
        ws.baseCurrency shouldBe CurrencyCode("USD")
        ws.ownerId shouldBe OWNER_ID
        ws.archived shouldBe false
        val createdAt = ws.createdAt
        (createdAt in before..after) shouldBe true

        env.memberRepo.inserted shouldHaveSize 1
        val member = env.memberRepo.inserted.single()
        member.userId shouldBe OWNER_ID
        member.workspaceId shouldBe newId
        member.role shouldBe WorkspaceRole.OWNER
        member.createdAt shouldBe createdAt
        member.addedByUserId shouldBe OWNER_ID

        env.categoryRepo.inserted shouldHaveSize DEFAULT_CATEGORY_SEEDS.size
        env.categoryRepo.inserted.map { it.workspaceId }.toSet() shouldBe setOf(newId)
        env.categoryRepo.inserted.map { it.createdAt }.toSet() shouldBe setOf(createdAt)
        env.categoryRepo.inserted.map { it.parentId }.toSet() shouldBe setOf(null)
        env.categoryRepo.inserted.map { it.id.value }.toSet() shouldHaveSize DEFAULT_CATEGORY_SEEDS.size
        env.categoryRepo.inserted.map { it.name to it.type } shouldContainExactlyInAnyOrder
            DEFAULT_CATEGORY_SEEDS.map { it.name to it.type }

        env.syncer.pushAllCount shouldBe 0
        env.userRemoteRepo.addRefCalls shouldHaveSize 0
        env.userRemoteRepo.setDefaultCalls shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe newId
    }

    "trims name and description before insert" {
        val env = TestEnv(currentUserId = OWNER_ID)

        env.useCase(
            CreateWorkspaceUseCase.Params(
                name = "  Family  ",
                description = "\tShared bills\n",
                baseCurrency = CurrencyCode("EUR"),
            ),
        ).shouldBeInstanceOf<Either.Right<WorkspaceId>>()

        val ws = env.workspaceRepo.inserted.single()
        ws.name shouldBe "Family"
        ws.description shouldBe "Shared bills"
    }

    "pushes to Firestore and updates user ref + default when Firebase uid present" {
        val env = TestEnv(currentUserId = OWNER_ID, firebaseUid = FIREBASE_UID)

        val result = env.useCase(defaultParams())

        val newId = (result as Either.Right).value
        env.syncer.pushAllCount shouldBe 1
        env.userRemoteRepo.addRefCalls shouldBe listOf(FIREBASE_UID to newId)
        env.userRemoteRepo.setDefaultCalls shouldBe listOf(FIREBASE_UID to newId)
        env.session.currentWorkspaceId.flow.first() shouldBe newId
    }

    "still pins workspace when setDefaultWorkspace throws" {
        val env = TestEnv(
            currentUserId = OWNER_ID,
            firebaseUid = FIREBASE_UID,
            userRemoteRepo = FakeUserRemoteRepository(failOnSetDefault = true),
        )

        val result = env.useCase(defaultParams())

        result.shouldBeInstanceOf<Either.Right<WorkspaceId>>()
        val newId = result.value
        env.userRemoteRepo.addRefCalls shouldBe listOf(FIREBASE_UID to newId)
        env.userRemoteRepo.setDefaultCalls shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe newId
    }

    "pushAll failure surfaces as RemoteSyncFailed and skips addRef + pin" {
        val env = TestEnv(
            currentUserId = OWNER_ID,
            firebaseUid = FIREBASE_UID,
            syncer = FakeWorkspaceSyncer(failOnSync = true),
        )

        val result = env.useCase(defaultParams())

        // UI must surface this — silent success used to send users to a dead-end selector.
        val left = result.shouldBeInstanceOf<Either.Left<CreateWorkspaceError>>()
        left.value.shouldBeInstanceOf<CreateWorkspaceError.RemoteSyncFailed>()

        // Local rows are kept (idempotent retry friendly), but no remote-user mutations
        // and no pin happen — `users/{uid}.workspaceIds` would otherwise reference a wid
        // whose `members/{uid}` row never landed (firestore.rules `isMember`).
        env.workspaceRepo.inserted.single().id shouldBe (env.workspaceRepo.inserted.single().id)
        env.userRemoteRepo.addRefCalls shouldHaveSize 0
        env.userRemoteRepo.setDefaultCalls shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }

    "still pins workspace and succeeds when addWorkspaceRef throws" {
        val env = TestEnv(
            currentUserId = OWNER_ID,
            firebaseUid = FIREBASE_UID,
            userRemoteRepo = FakeUserRemoteRepository(failOnAddRef = true),
        )

        val result = env.useCase(defaultParams())

        result.shouldBeInstanceOf<Either.Right<WorkspaceId>>()
        val newId = result.value
        env.syncer.pushAllCount shouldBe 1
        env.session.currentWorkspaceId.flow.first() shouldBe newId
    }

    "local insert failure returns LocalWriteFailed and skips remote work" {
        val boom = RuntimeException("disk full")
        val env = TestEnv(
            currentUserId = OWNER_ID,
            firebaseUid = FIREBASE_UID,
            workspaceRepo = FakeWorkspaceRepository(failOnInsert = boom),
        )

        val result = env.useCase(defaultParams())

        val left = result.shouldBeInstanceOf<Either.Left<CreateWorkspaceError>>()
        val error = left.value.shouldBeInstanceOf<CreateWorkspaceError.LocalWriteFailed>()
        error.cause shouldBe boom
        env.memberRepo.inserted shouldHaveSize 0
        env.categoryRepo.inserted shouldHaveSize 0
        env.syncer.pushAllCount shouldBe 0
        env.userRemoteRepo.addRefCalls shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }

    // -- Regression coverage: workspaceIds must be filled on the happy path --
    // Bug surfaces as "users/{uid}.workspaceIds remains empty after creation" when one of the
    // remote calls is silently skipped. Lock in the contract.

    "remote calls fire in strict order: syncWorkspace → addWorkspaceRef → setDefaultWorkspace" {
        val env = TestEnv(currentUserId = OWNER_ID, firebaseUid = FIREBASE_UID)

        env.useCase(defaultParams()).shouldBeInstanceOf<Either.Right<WorkspaceId>>()

        // Order matters: addWorkspaceRef must precede setDefaultWorkspace (otherwise the
        // server-side validation `defaultWorkspaceId in workspaceIds` would fail when we add
        // it, see md/rules-bugs.md #4).
        env.callLog shouldBe listOf(
            "pushAll",
            "addWorkspaceRef",
            "setDefaultWorkspace",
        )
    }

    "addWorkspaceRef fires with the new workspace id so users/{uid}.workspaceIds is filled" {
        val env = TestEnv(currentUserId = OWNER_ID, firebaseUid = FIREBASE_UID)

        val newId = (env.useCase(defaultParams()) as Either.Right).value

        // Regression: the bug looked like `addRefCalls` being empty after creation. This
        // assertion locks in that the ref IS pushed when sync succeeds.
        env.userRemoteRepo.addRefCalls shouldBe listOf(FIREBASE_UID to newId)
    }

    "addWorkspaceRef failure does not block setDefaultWorkspace" {
        // Each remote write is its own best-effort `Either.catch`. If `addWorkspaceRef`
        // throws, the next statement still runs. Locks in the contract so a future
        // tightening (e.g. gate setDefault on addRef success) is explicit.
        // Known follow-up: server-side default may then reference a wid not yet in
        // `workspaceIds` — see md/rules-bugs.md #4.
        val sharedLog = mutableListOf<String>()
        val env = TestEnv(
            currentUserId = OWNER_ID,
            firebaseUid = FIREBASE_UID,
            callLog = sharedLog,
            userRemoteRepo = FakeUserRemoteRepository(failOnAddRef = true, callLog = sharedLog),
            syncer = FakeWorkspaceSyncer(callLog = sharedLog),
        )

        env.useCase(defaultParams()).shouldBeInstanceOf<Either.Right<WorkspaceId>>()

        env.userRemoteRepo.addRefCalls shouldHaveSize 0
        env.userRemoteRepo.setDefaultCalls shouldHaveSize 1
        // addWorkspaceRef threw before the callLog append — only sync + setDefault recorded.
        sharedLog shouldBe listOf("pushAll", "setDefaultWorkspace")
    }

    "no remote calls when no Firebase uid (demo session)" {
        val env = TestEnv(currentUserId = OWNER_ID, firebaseUid = null)

        env.useCase(defaultParams()).shouldBeInstanceOf<Either.Right<WorkspaceId>>()

        env.callLog shouldHaveSize 0
        env.userRemoteRepo.addRefCalls shouldHaveSize 0
        env.userRemoteRepo.setDefaultCalls shouldHaveSize 0
    }
})

private val OWNER_ID = UserId("owner-uuid")
private const val FIREBASE_UID = "firebase-uid-123"

private fun defaultParams() = CreateWorkspaceUseCase.Params(
    name = "My Budget",
    description = "Personal expenses",
    baseCurrency = CurrencyCode("USD"),
)

private class TestEnv(
    currentUserId: UserId?,
    firebaseUid: String? = null,
    val callLog: MutableList<String> = mutableListOf(),
    val workspaceRepo: FakeWorkspaceRepository = FakeWorkspaceRepository(),
    val memberRepo: FakeWorkspaceMemberRepository = FakeWorkspaceMemberRepository(),
    val categoryRepo: FakeCategoryRepository = FakeCategoryRepository(),
    val userRemoteRepo: FakeUserRemoteRepository = FakeUserRemoteRepository(callLog = callLog),
    val syncer: FakeWorkspaceSyncer = FakeWorkspaceSyncer(callLog = callLog),
) {
    val session = InMemorySessionPointers(
        currentUserId = currentUserId,
        currentFirebaseUid = firebaseUid,
    )

    val useCase = CreateWorkspaceUseCase(
        workspaceRepository = workspaceRepo,
        workspaceMemberRepository = memberRepo,
        categoryRepository = categoryRepo,
        userRemoteRepository = userRemoteRepo,
        workspaceSyncer = syncer,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    )
}

private class FakeWorkspaceRepository(
    private val failOnInsert: Throwable? = null,
) : WorkspaceRepository {
    val inserted = mutableListOf<Workspace>()
    override fun getAll(): Flow<List<Workspace>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(emptyList())
    override suspend fun getById(id: WorkspaceId): Workspace? = inserted.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) {
        failOnInsert?.let { throw it }
        inserted += workspace
    }
    override suspend fun update(workspace: Workspace) = error("not used")
    override suspend fun delete(id: WorkspaceId) = error("not used")
}

private class FakeWorkspaceMemberRepository : WorkspaceMemberRepository {
    val inserted = mutableListOf<WorkspaceMember>()
    override fun getAll(): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? = null
    override suspend fun insert(member: WorkspaceMember) {
        inserted += member
    }
    override suspend fun update(member: WorkspaceMember) = error("not used")
    override suspend fun markRemoved(
        userId: UserId,
        workspaceId: WorkspaceId,
        removedByUserId: UserId?,
    ) = error("not used")
    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) = error("not used")
}

private class FakeCategoryRepository : CategoryRepository {
    val inserted = mutableListOf<Category>()
    override fun getAll(): Flow<List<Category>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getById(id: CategoryId): Category? = inserted.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) {
        inserted += category
    }
    override suspend fun update(category: Category) = error("not used")
    override suspend fun delete(id: CategoryId) = error("not used")
}

private class FakeUserRemoteRepository(
    private val failOnAddRef: Boolean = false,
    private val failOnSetDefault: Boolean = false,
    private val callLog: MutableList<String> = mutableListOf(),
) : UserRemoteRepository {
    val addRefCalls = mutableListOf<Pair<String, WorkspaceId>>()
    val setDefaultCalls = mutableListOf<Pair<String, WorkspaceId>>()
    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(uid: String, displayName: String?, email: String?, isAnon: Boolean, createdAt: Long) =
        error("not used")
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) {
        if (failOnAddRef) throw RuntimeException("network down")
        callLog += "addWorkspaceRef"
        addRefCalls += uid to workspaceId
    }
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) {
        if (failOnSetDefault) throw RuntimeException("network down")
        callLog += "setDefaultWorkspace"
        setDefaultCalls += uid to workspaceId
    }
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private class FakeWorkspaceSyncer(
    private val failOnSync: Boolean = false,
    private val callLog: MutableList<String> = mutableListOf(),
) : WorkspaceSyncer {
    var pushAllCount = 0
    override suspend fun pushAll() {
        if (failOnSync) throw RuntimeException("firestore unavailable")
        callLog += "pushAll"
        pushAllCount++
    }
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
