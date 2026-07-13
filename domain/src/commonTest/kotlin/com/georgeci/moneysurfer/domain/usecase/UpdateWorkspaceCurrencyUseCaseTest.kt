package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

private val GBP = CurrencyCode("GBP")

class UpdateWorkspaceCurrencyUseCaseTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    "updates the workspace base currency" {
        runTest {
            val workspaceRepo = CurrencyTestWorkspaceRepository().apply {
                seed(aWorkspace(id = ws, baseCurrency = USD))
            }
            val accountRepo = CurrencyTestAccountRepository()
            val useCase = UpdateWorkspaceCurrencyUseCase(workspaceRepo, accountRepo)

            useCase(ws, GBP).isRight() shouldBe true

            workspaceRepo.getById(ws)!!.baseCurrency shouldBe GBP
        }
    }

    "re-currencies accounts still on the previous workspace currency" {
        runTest {
            val workspaceRepo = CurrencyTestWorkspaceRepository().apply {
                seed(aWorkspace(id = ws, baseCurrency = USD))
            }
            val accountRepo = CurrencyTestAccountRepository().apply {
                seed(anAccount(id = AccountId("a-cash"), workspaceId = ws, currencyCode = USD))
            }
            val useCase = UpdateWorkspaceCurrencyUseCase(workspaceRepo, accountRepo)

            useCase(ws, GBP).isRight() shouldBe true

            accountRepo.getById(AccountId("a-cash"))!!.currencyCode shouldBe GBP
        }
    }

    "repairs lagging accounts when the workspace currency already matches (retry path)" {
        runTest {
            // Simulates a retry after a partial failure: the workspace write landed last time
            // but an account write did not, so the account still trails the workspace.
            val workspaceRepo = CurrencyTestWorkspaceRepository().apply {
                seed(aWorkspace(id = ws, baseCurrency = GBP))
            }
            val accountRepo = CurrencyTestAccountRepository().apply {
                seed(anAccount(id = AccountId("a-cash"), workspaceId = ws, currencyCode = USD))
            }
            val useCase = UpdateWorkspaceCurrencyUseCase(workspaceRepo, accountRepo)

            useCase(ws, GBP).isRight() shouldBe true

            accountRepo.getById(AccountId("a-cash"))!!.currencyCode shouldBe GBP
            workspaceRepo.updateCount shouldBe 0
        }
    }

    "is a no-op when the requested currency already matches" {
        runTest {
            val workspaceRepo = CurrencyTestWorkspaceRepository().apply {
                seed(aWorkspace(id = ws, baseCurrency = USD))
            }
            val accountRepo = CurrencyTestAccountRepository()
            val useCase = UpdateWorkspaceCurrencyUseCase(workspaceRepo, accountRepo)

            useCase(ws, USD).isRight() shouldBe true

            workspaceRepo.updateCount shouldBe 0
        }
    }

    "raises WorkspaceNotFound when the workspace does not exist" {
        runTest {
            val useCase = UpdateWorkspaceCurrencyUseCase(
                CurrencyTestWorkspaceRepository(),
                CurrencyTestAccountRepository(),
            )

            val result = useCase(ws, GBP)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<UpdateWorkspaceCurrencyError.WorkspaceNotFound>()
        }
    }
})

private class CurrencyTestWorkspaceRepository : WorkspaceRepository {
    private val byId = mutableMapOf<WorkspaceId, Workspace>()
    private val all = MutableStateFlow<List<Workspace>>(emptyList())
    var updateCount: Int = 0
        private set

    fun seed(vararg workspaces: Workspace) {
        workspaces.forEach { byId[it.id] = it }
        all.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Workspace>> = all
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = all
    override suspend fun getById(id: WorkspaceId): Workspace? = byId[id]
    override suspend fun insert(workspace: Workspace) {
        byId[workspace.id] = workspace
        all.value = byId.values.toList()
    }
    override suspend fun update(workspace: Workspace) {
        updateCount++
        byId[workspace.id] = workspace
        all.value = byId.values.toList()
    }
    override suspend fun delete(id: WorkspaceId) {
        byId.remove(id)
        all.value = byId.values.toList()
    }
}

private class CurrencyTestAccountRepository : AccountRepository {
    private val byId = mutableMapOf<AccountId, Account>()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())

    fun seed(vararg accounts: Account) {
        accounts.forEach { byId[it.id] = it }
        byWorkspace.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Account>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = byWorkspace
    override suspend fun getById(id: AccountId): Account? = byId[id]
    override suspend fun insert(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun update(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun delete(id: AccountId) {
        byId.remove(id)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = current.balance + delta)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = balance)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(archived = archived)
        byWorkspace.value = byId.values.toList()
    }
}
