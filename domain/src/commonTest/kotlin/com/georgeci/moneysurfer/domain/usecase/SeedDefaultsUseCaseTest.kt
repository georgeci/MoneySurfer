package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

class SeedDefaultsUseCaseTest : StringSpec({

    "does not create a second workspace when one is already pinned" {
        val env = SeedTestEnv(
            currentUserId = OWNER_ID,
            currentWorkspaceId = PRE_PINNED,
        )

        env.useCase(CurrencyCode("USD"))

        env.workspaceRepo.inserted shouldHaveSize 0
        env.categoryRepo.inserted shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe PRE_PINNED
    }

    "repairs missing Cash account when workspace was pinned without one" {
        // Simulates a previous run that died after pinning the workspace but before the Cash
        // insert — Copilot review feedback on PR #91. Subsequent launches must heal that state.
        val env = SeedTestEnv(
            currentUserId = OWNER_ID,
            currentWorkspaceId = PRE_PINNED,
        )

        env.useCase(CurrencyCode("USD"))

        val account = env.accountRepo.inserted.single()
        account.name shouldBe "Cash"
        account.type shouldBe AccountType.CASH
        account.workspaceId shouldBe PRE_PINNED
    }

    "creates workspace, default categories, and a Cash account on first run" {
        val env = SeedTestEnv(currentUserId = OWNER_ID)

        env.useCase(CurrencyCode("USD"))

        val ws = env.workspaceRepo.inserted.single()
        ws.name shouldBe "Personal"
        ws.baseCurrency shouldBe CurrencyCode("USD")

        env.categoryRepo.inserted shouldHaveSize DEFAULT_CATEGORY_SEEDS.size

        val account = env.accountRepo.inserted.single()
        account.name shouldBe "Cash"
        account.type shouldBe AccountType.CASH
        account.workspaceId shouldBe ws.id
        account.currencyCode shouldBe CurrencyCode("USD")
        account.balance shouldBe Money.zero()

        env.session.currentWorkspaceId.flow.first() shouldBe ws.id
    }

    "is idempotent — second invocation does not duplicate workspace or Cash account" {
        val env = SeedTestEnv(currentUserId = OWNER_ID)

        env.useCase(CurrencyCode("USD"))
        env.useCase(CurrencyCode("USD"))

        env.workspaceRepo.inserted shouldHaveSize 1
        env.accountRepo.inserted shouldHaveSize 1
        env.categoryRepo.inserted shouldHaveSize DEFAULT_CATEGORY_SEEDS.size
    }

    "no-ops when CreateWorkspaceUseCase has no current user (offline build pre-login)" {
        val env = SeedTestEnv(currentUserId = null)

        env.useCase(CurrencyCode("USD"))

        env.workspaceRepo.inserted shouldHaveSize 0
        env.accountRepo.inserted shouldHaveSize 0
        env.session.currentWorkspaceId.flow.first() shouldBe null
    }
})

private val OWNER_ID = UserId("seed-owner")
private val PRE_PINNED = WorkspaceId("pre-pinned-wid")

private class SeedTestEnv(
    currentUserId: UserId?,
    currentWorkspaceId: WorkspaceId? = null,
) {
    val workspaceRepo = FakeWorkspaceRepo()
    val memberRepo = FakeMemberRepo()
    val categoryRepo = FakeCategoryRepo()
    val accountRepo = FakeAccountRepo()
    val session = InMemorySessionPointers(
        currentUserId = currentUserId,
        currentWorkspaceId = currentWorkspaceId,
    )
    val getCurrentTime = GetCurrentTimeUseCase(ClockUseCase())
    val createWorkspace = CreateWorkspaceUseCase(
        workspaceRepository = workspaceRepo,
        workspaceMemberRepository = memberRepo,
        categoryRepository = categoryRepo,
        userRemoteRepository = SeedFakeUserRemoteRepo,
        workspaceSyncer = SeedFakeWorkspaceSyncer,
        session = session,
        getCurrentTime = getCurrentTime,
        // Offline seed path — there is no Firebase session here either way.
        syncFeatureFlag = SyncFeatureFlag(enabled = false),
    )
    val useCase = SeedDefaultsUseCase(
        createWorkspace = createWorkspace,
        accountRepository = accountRepo,
        workspaceRepository = workspaceRepo,
        session = session,
        getCurrentTime = getCurrentTime,
    )
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
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = error("not used")
}

private object SeedFakeUserRemoteRepo : UserRemoteRepository {
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

private object SeedFakeWorkspaceSyncer : WorkspaceSyncer {
    override suspend fun pushAll() = Unit
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
