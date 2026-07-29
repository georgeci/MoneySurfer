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
import com.georgeci.moneysurfer.domain.usecase.DeleteAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.ReorderAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreAccountUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_undo
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_deleted_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    "OnArchiveConfirm archives the account, clears pending, and shows an undo snackbar" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))

        snackbar.requests.test {
            viewModel.onEvent(AccountsManageEvent.OnArchiveConfirm)
            val request = awaitItem()
            request.message shouldBe Res.string.accounts_manage_archived_snackbar
            request.messageArgs shouldBe listOf("Everyday")
            request.actionLabel shouldBe Res.string.accounts_manage_archive_undo
        }

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.pendingArchive shouldBe null
        content.activeAccounts.shouldBeEmpty()
        content.archivedAccounts.single().id shouldBe active.id
        repo.snapshot().single().archived shouldBe true
    }

    "OnDeleteConfirm removes the account and shows a snackbar" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(AccountsManageEvent.OnRemoveAccountClick(active.id))

        snackbar.requests.test {
            viewModel.onEvent(AccountsManageEvent.OnDeleteConfirm)
            val request = awaitItem()
            request.message shouldBe Res.string.accounts_manage_deleted_snackbar
            request.messageArgs shouldBe listOf("Everyday")
        }

        repo.snapshot().shouldBeEmpty()
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

    "tapping Undo on the archive snackbar restores the account" {
        val ws = workspaceId("ws-1")
        val active = anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday")
        val repo = FakeAccountRepository(initial = listOf(active), workspaceId = ws)
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(active.id))

        var onUndo: (suspend () -> Unit)? = null
        snackbar.requests.test {
            viewModel.onEvent(AccountsManageEvent.OnArchiveConfirm)
            onUndo = awaitItem().onAction
        }
        repo.snapshot().single().archived shouldBe true

        onUndo.shouldNotBeNull().invoke()

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.archivedAccounts.shouldBeEmpty()
        content.activeAccounts.single().id shouldBe active.id
        repo.snapshot().single().archived shouldBe false
    }

    "the active list is shown in sortOrder, not in the order the repository happens to hold" {
        val ws = workspaceId("ws-1")
        val repo = FakeAccountRepository(
            initial = listOf(
                anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday").copy(sortOrder = 2),
                anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings").copy(sortOrder = 0),
                anAccount(id = accountId("a-3"), workspaceId = ws, name = "Card").copy(sortOrder = 1),
            ),
            workspaceId = ws,
        )
        val viewModel = newViewModel(repo, ws)

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.activeAccounts.map { it.name } shouldBe listOf("Savings", "Card", "Everyday")
    }

    "OnAccountMove reorders the list on screen and writes nothing until the row is dropped" {
        val ws = workspaceId("ws-1")
        val (first, second, third) = threeAccounts(ws)
        val repo = FakeAccountRepository(initial = listOf(first, second, third), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnAccountMove(from = third.id, to = first.id))

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.activeAccounts.map { it.id } shouldBe listOf(third.id, first.id, second.id)
        repo.snapshot().map { it.sortOrder } shouldBe listOf(0, 1, 2)
    }

    "OnAccountMoveEnd persists the order the drag built up" {
        val ws = workspaceId("ws-1")
        val (first, second, third) = threeAccounts(ws)
        val repo = FakeAccountRepository(initial = listOf(first, second, third), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnAccountMove(from = third.id, to = first.id))
        viewModel.onEvent(AccountsManageEvent.OnAccountMoveEnd)

        repo.snapshot().map { it.id to it.sortOrder } shouldBe
            listOf(third.id to 0, first.id to 1, second.id to 2)
        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.activeAccounts.map { it.id } shouldBe listOf(third.id, first.id, second.id)
    }

    "a repository emission mid-drag does not snap the rows back" {
        val ws = workspaceId("ws-1")
        val (first, second, third) = threeAccounts(ws)
        val repo = FakeAccountRepository(initial = listOf(first, second, third), workspaceId = ws)
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(AccountsManageEvent.OnAccountMove(from = third.id, to = first.id))
        // Anything else that touches the table — a balance recalculation, a sync pull — re-emits
        // the stored order while the drag is still in progress.
        repo.update(second.copy(name = "Renamed"))

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.activeAccounts.map { it.id } shouldBe listOf(third.id, first.id, second.id)
    }

    "navigation follows edit mode and carries the clicked account id" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val viewModel = newViewModel(FakeAccountRepository(listOf(account), ws), ws)

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(AccountsManageEvent.OnBackClick)
            awaitItem() shouldBe AccountsManageEffect.NavigateBack
            viewModel.onEvent(AccountsManageEvent.OnAddAccountClick)
            awaitItem() shouldBe AccountsManageEffect.NavigateToAccountCreation
            viewModel.onEvent(AccountsManageEvent.OnAccountClick(account.id))
            awaitItem() shouldBe AccountsManageEffect.NavigateToAccountDetails(account.id)
            viewModel.onEvent(AccountsManageEvent.OnToggleEditMode)
            viewModel.onEvent(AccountsManageEvent.OnAccountClick(account.id))
            awaitItem() shouldBe AccountsManageEffect.NavigateToAccountEdit(account.id)
        }
    }

    "invalid drag and destructive events are safe no-ops" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val repo = FakeAccountRepository(listOf(account), ws)
        val viewModel = newViewModel(repo, ws)
        val missing = accountId("missing")

        viewModel.onEvent(AccountsManageEvent.OnAccountMove(account.id, account.id))
        viewModel.onEvent(AccountsManageEvent.OnAccountMove(account.id, missing))
        viewModel.onEvent(AccountsManageEvent.OnArchiveAccountClick(missing))
        viewModel.onEvent(AccountsManageEvent.OnRemoveAccountClick(missing))
        viewModel.onEvent(AccountsManageEvent.OnArchiveConfirm)
        viewModel.onEvent(AccountsManageEvent.OnDeleteConfirm)

        val content = viewModel.value.shouldBeInstanceOf<AccountsManageState.Content>()
        content.activeAccounts.map { it.id } shouldBe listOf(account.id)
        content.pendingArchive shouldBe null
        content.pendingDelete shouldBe null
        repo.snapshot().map { it.id } shouldBe listOf(account.id)
    }
})

private fun threeAccounts(ws: WorkspaceId): Triple<Account, Account, Account> = Triple(
    anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday").copy(sortOrder = 0),
    anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings").copy(sortOrder = 1),
    anAccount(id = accountId("a-3"), workspaceId = ws, name = "Card").copy(sortOrder = 2),
)

private fun StringSpec.newViewModel(
    repo: FakeAccountRepository,
    workspaceId: WorkspaceId,
    snackbar: SnackbarController = SnackbarController(),
): AccountsManageViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    return AccountsManageViewModel(
        getAccounts = GetAccountsUseCase(repo, session),
        deleteAccount = DeleteAccountUseCase(repo),
        archiveAccount = ArchiveAccountUseCase(repo),
        restoreAccount = RestoreAccountUseCase(repo),
        reorderAccounts = ReorderAccountsUseCase(repo),
        snackbar = snackbar,
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

    /** Reads come back the way `AccountDao` orders them, so the tests see what the screen sees. */
    private val ordered = state.map { list -> list.sortedWith(ACCOUNT_ORDER) }

    fun snapshot(): List<Account> = state.value.sortedWith(ACCOUNT_ORDER)

    override fun getAll(): Flow<List<Account>> = ordered
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = ordered
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

    override suspend fun reorder(orderedIds: List<AccountId>) {
        val positions = orderedIds.withIndex().associate { (position, id) -> id to position }
        state.update { list ->
            list.map { account -> positions[account.id]?.let { account.copy(sortOrder = it) } ?: account }
        }
    }
}

private val ACCOUNT_ORDER = compareBy<Account>({ it.sortOrder }, { it.name })
