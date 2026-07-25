package com.georgeci.moneysurfer.offline.firstrun

import com.georgeci.moneysurfer.domain.auth.AuthLocalRepository
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.config.SyncSettings
import com.georgeci.moneysurfer.domain.constants.PREFILLED_DEFAULT_USER_ID
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.SeedDefaultsUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * The offline build's first-run bootstrap. Unlike [SeedDefaultsUseCase] (tested separately), the
 * seeder adds three offline-specific behaviours we cover here:
 *  - pre-seed an anonymous demo user when none is pinned, so the seed can run without a sign-in tap;
 *  - seed the workspace but *no* account — the user creates the first one after onboarding;
 *  - abort cleanly when the demo login fails, leaving no partially-seeded state behind.
 *
 * The real [SeedDefaultsUseCase] + [DemoLoginUseCase] run against in-memory fakes so the test
 * exercises the full offline first-run chain, not just the seeder's own branches.
 */
class OfflineFirstRunSeederTest : StringSpec({

    "first run pins a demo user and seeds Personal without any account" {
        runTest {
            val env = SeederEnv()

            env.seeder.seedIfNeeded()

            // Demo user was pinned so the seed had a current user to attribute the workspace to.
            env.session.currentUserId.first() shouldBe UserId(PREFILLED_DEFAULT_USER_ID)

            val workspace = env.workspaceRepo.inserted.single()
            workspace.name shouldBe "Personal"
            env.session.currentWorkspaceId.first() shouldBe workspace.id
            // The first account is created by the user on the first-run account screen.
            env.accountRepo.inserted shouldHaveSize 0
        }
    }

    "relaunch is idempotent — no duplicate workspace and still no seeded account" {
        runTest {
            val env = SeederEnv()

            env.seeder.seedIfNeeded()
            env.seeder.seedIfNeeded()

            env.workspaceRepo.inserted shouldHaveSize 1
            env.accountRepo.inserted shouldHaveSize 0
        }
    }

    "existing install with a pinned workspace is left alone and does not re-login" {
        runTest {
            val env = SeederEnv(
                currentUserId = OWNER_ID,
                currentWorkspaceId = PRE_PINNED,
            )

            env.seeder.seedIfNeeded()

            env.workspaceRepo.inserted shouldHaveSize 0
            env.accountRepo.inserted shouldHaveSize 0
            // A user was already present, so the demo login path never ran.
            env.userRepo.upserts shouldHaveSize 0
        }
    }

    "aborts without seeding when the demo login fails" {
        runTest {
            val env = SeederEnv(failUserUpsert = true)

            env.seeder.seedIfNeeded()

            // DemoLogin blew up before pinning the user, so the seed was skipped entirely.
            env.session.currentUserId.first() shouldBe null
            env.session.currentWorkspaceId.first() shouldBe null
            env.workspaceRepo.inserted shouldHaveSize 0
            env.accountRepo.inserted shouldHaveSize 0
        }
    }
})

private val OWNER_ID = UserId("seed-owner")
private val PRE_PINNED = WorkspaceId("pre-pinned-wid")

private class SeederEnv(
    currentUserId: UserId? = null,
    currentWorkspaceId: WorkspaceId? = null,
    failUserUpsert: Boolean = false,
) {
    val session = InMemorySessionPointers(
        currentUserId = currentUserId,
        currentWorkspaceId = currentWorkspaceId,
    )
    val userRepo = FakeUserRepo(failUpsert = failUserUpsert)
    val workspaceRepo = FakeWorkspaceRepo()
    val memberRepo = FakeMemberRepo()
    val categoryRepo = FakeCategoryRepo()
    val accountRepo = FakeAccountRepo()
    private val getCurrentTime = GetCurrentTimeUseCase(ClockUseCase())
    private val demoLogin = DemoLoginUseCase(
        authLocalRepository = AuthLocalRepository(userRepo, session),
        sessionMutator = session,
    )
    private val createWorkspace = CreateWorkspaceUseCase(
        workspaceRepository = workspaceRepo,
        workspaceMemberRepository = memberRepo,
        categoryRepository = categoryRepo,
        userRemoteRepository = SeederFakeUserRemoteRepo,
        workspaceSyncer = SeederFakeWorkspaceSyncer,
        session = session,
        sessionMutator = session,
        getCurrentTime = getCurrentTime,
        // Offline build: sync is off, exactly as `offlineConfigModule` declares `host.sync_enabled`.
        syncSettings = SeederSyncOff,
    )
    private val seedDefaults = SeedDefaultsUseCase(
        createWorkspace = createWorkspace,
        accountRepository = accountRepo,
        workspaceRepository = workspaceRepo,
        session = session,
        getCurrentTime = getCurrentTime,
    )
    val seeder = OfflineFirstRunSeeder(
        session = session,
        demoLoginUseCase = demoLogin,
        seedDefaultsUseCase = seedDefaults,
    )
}

private class FakeUserRepo(private val failUpsert: Boolean) : UserRepository {
    val upserts = mutableListOf<User>()
    override fun getAll(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun getById(id: UserId): User? = upserts.firstOrNull { it.id == id }
    override suspend fun insert(user: User) = error("not used")
    override suspend fun update(user: User) = error("not used")
    override suspend fun upsert(user: User) {
        if (failUpsert) error("simulated local user write failure")
        upserts += user
    }
    override suspend fun delete(id: UserId) = error("not used")
}

private class FakeWorkspaceRepo : WorkspaceRepository {
    val inserted = mutableListOf<Workspace>()
    override fun getAll(): Flow<List<Workspace>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(emptyList())
    override suspend fun getById(id: WorkspaceId): Workspace? = inserted.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) {
        inserted += workspace
    }
    override suspend fun update(workspace: Workspace) = error("not used")
    override suspend fun delete(id: WorkspaceId) = error("not used")
}

private class FakeMemberRepo : WorkspaceMemberRepository {
    val inserted = mutableListOf<WorkspaceMember>()
    override fun getAll(): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override fun getByUserId(userId: UserId): Flow<List<WorkspaceMember>> = flowOf(emptyList())
    override suspend fun getById(userId: UserId, workspaceId: WorkspaceId): WorkspaceMember? = null
    override suspend fun insert(member: WorkspaceMember) {
        inserted += member
    }
    override suspend fun update(member: WorkspaceMember) = error("not used")
    override suspend fun markRemoved(userId: UserId, workspaceId: WorkspaceId, removedByUserId: UserId?) =
        error("not used")
    override suspend fun markLeft(userId: UserId, workspaceId: WorkspaceId) = error("not used")
}

private class FakeCategoryRepo : CategoryRepository {
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

private class FakeAccountRepo : AccountRepository {
    val inserted = mutableListOf<Account>()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())
    override fun getAll(): Flow<List<Account>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> {
        byWorkspace.value = inserted.filter { it.workspaceId == workspaceId }
        return byWorkspace
    }
    override suspend fun getById(id: AccountId): Account? = inserted.firstOrNull { it.id == id }
    override suspend fun insert(account: Account) {
        inserted += account
    }
    override suspend fun update(account: Account) = error("not used")
    override suspend fun delete(id: AccountId) = error("not used")
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = error("not used")
    override suspend fun setBalance(accountId: AccountId, balance: Money) = error("not used")
    override suspend fun reorder(orderedIds: List<AccountId>) = error("not used")
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = error("not used")
}

private object SeederFakeUserRemoteRepo : UserRemoteRepository {
    override suspend fun fetch(uid: String): User? = null
    override suspend fun create(uid: String, displayName: String?, email: String?, isAnon: Boolean, createdAt: Long) =
        error("not used")
    override suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun findByEmail(email: String) = null
    override suspend fun upsertEmailMapping(email: String, uid: String) = Unit
    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId) = Unit
}

private object SeederFakeWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll() = Unit
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}

/**
 * The offline build's sync term: always off, exactly as `offlineConfigModule` declares
 * `host.sync_enabled`. Local rather than from `domain-test-fixtures`, which this source set
 * does not depend on.
 */
private object SeederSyncOff : SyncSettings {
    override val isEnabled: Flow<Boolean> = flowOf(false)
}
