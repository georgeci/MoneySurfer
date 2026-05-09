package com.georgeci.moneysurfer.feature.account.manage

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.ArchiveAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreAccountUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsManageViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "OnArchiveAccountClick stages pendingArchive without mutating the account" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.pendingArchive?.id shouldBe active.id
        repo.snapshot().single().archived shouldBe false
    }

    "OnArchiveConfirm archives the account, clears pending, and posts a snackbar effect" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(AccountsManageEvent.OnArchiveConfirm)
            val effect = awaitItem().shouldBeInstanceOf<AccountsManageEffect.ShowArchivedSnackbar>()
            effect.accountId shouldBe active.id
            effect.name shouldBe "Everyday"
        }

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.pendingArchive shouldBe null
        content.activeAccounts.shouldBeEmpty()
        content.archivedAccounts.single().id shouldBe active.id
        repo.snapshot().single().archived shouldBe true
    }

    "OnArchiveCancel clears the pending archive without changes" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))
        viewModel.onEvent(AccountsManageEvent.OnArchiveCancel)

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.pendingArchive shouldBe null
        repo.snapshot().single().archived shouldBe false
    }

    "OnRestoreAccountClick moves an archived account back to active" {
        val ws = workspaceId("ws-1")
        val archived = anAccount(id = accountId("a-1"), workspaceId = ws, archived = true)
        val repo = FakeAccountRepository(initial = listOf(archived), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnRestoreAccountClick(archived.id))

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.archivedAccounts.shouldBeEmpty()
        content.activeAccounts.single().id shouldBe archived.id
        repo.snapshot().single().archived shouldBe false
    }

    "OnUndoArchive restores the most recently archived account" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))
        viewModel.onEvent(AccountsManageEvent.OnArchiveConfirm)
        viewModel.onEvent(AccountsManageEvent.OnUndoArchive(active.id))

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.archivedAccounts.shouldBeEmpty()
        content.activeAccounts.single().id shouldBe active.id
        repo.snapshot().single().archived shouldBe false
    }
})

private fun StringSpec.newViewModel(
    repo: FakeAccountRepository,
    workspaceId: WorkspaceId,
): AccountsManageViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    return AccountsManageViewModel(
        getAccounts = GetAccountsUseCase(repo, session),
        accountRepository = repo,
        archiveAccount = ArchiveAccountUseCase(repo),
        restoreAccount = RestoreAccountUseCase(repo),
    )
}

private fun List<*>.shouldBeEmpty() {
    if (isNotEmpty()) error("expected empty list, got $this")
}

private class FakeAccountRepository(
    initial: List<Account>,
    private val workspaceId: WorkspaceId,
) : AccountRepository {
    private val state = MutableStateFlow(initial)

    fun snapshot(): List<Account> = state.value

    override fun getAll(): Flow<List<Account>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = state
    override suspend fun getById(id: AccountId): Account? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(account: Account) {
        state.update { it + account }
    }
    override suspend fun update(account: Account) {
        state.update { list -> list.map { if (it.id == account.id) account else it } }
    }
    override suspend fun delete(id: AccountId) {
        state.update { it.filterNot { acc -> acc.id == id } }
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        state.update { list -> list.map { if (it.id == accountId) it.copy(archived = archived) else it } }
    }
}
