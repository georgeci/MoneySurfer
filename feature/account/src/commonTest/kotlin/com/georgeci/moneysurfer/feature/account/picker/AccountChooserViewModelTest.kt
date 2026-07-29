package com.georgeci.moneysurfer.feature.account.picker

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AccountChooserViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "stays loading and preserves the initial selection until accounts arrive" {
        val selected = accountId("selected")
        val viewModel = newViewModel(FakeAccountRepository(), selectedId = selected)

        viewModel.value shouldBe AccountChooserState.Loading(selected)
    }

    "publishes accounts and keeps the selected id" {
        val repo = FakeAccountRepository()
        val selected = accountId("cash")
        val viewModel = newViewModel(repo, selectedId = selected)
        repo.emit(
            listOf(
                anAccount(id = selected, name = "Cash"),
                anAccount(id = accountId("card"), name = "Card"),
            ),
        )

        val content = viewModel.content()

        content.accounts.map { it.name } shouldBe listOf("Cash", "Card")
        content.selectedId shouldBe selected
    }

    "excludes the transfer source account from both rows and totals" {
        val repo = FakeAccountRepository()
        val source = anAccount(id = accountId("source"), balance = 100.dollars)
        val destination = anAccount(id = accountId("destination"), balance = 25.dollars)
        val viewModel = newViewModel(repo, excludeAccountId = source.id)
        repo.emit(listOf(source, destination))

        val content = viewModel.content()

        content.accounts.map { it.id } shouldBe listOf(destination.id)
        content.totalsFormatted shouldBe listOf("$25.00")
    }

    "shows one total per currency instead of mixing minor units" {
        val repo = FakeAccountRepository()
        val viewModel = newViewModel(repo)
        repo.emit(
            listOf(
                anAccount(id = accountId("usd-1"), currencyCode = USD, balance = 10.dollars),
                anAccount(id = accountId("usd-2"), currencyCode = USD, balance = 5.dollars),
                anAccount(id = accountId("eur"), currencyCode = EUR, balance = 7.dollars),
            ),
        )

        viewModel.content().totalsFormatted shouldBe listOf("$15.00", "€7.00")
    }

    "an empty repository produces content with no total" {
        val repo = FakeAccountRepository()
        val viewModel = newViewModel(repo)
        repo.emit(emptyList())

        val content = viewModel.content()

        content.accounts shouldBe emptyList()
        content.totalsFormatted shouldBe emptyList()
    }

    "selection and sheet actions publish their corresponding effects" {
        val viewModel = newViewModel(FakeAccountRepository())
        val selected = accountId("cash")

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(AccountChooserEvent.OnAccountSelected(selected))
            awaitItem() shouldBe AccountChooserEffect.PublishResult(selected)
            viewModel.onEvent(AccountChooserEvent.OnAddNewAccountClick)
            awaitItem() shouldBe AccountChooserEffect.NavigateToAccountCreation
            viewModel.onEvent(AccountChooserEvent.OnTransferInsteadClick)
            awaitItem() shouldBe AccountChooserEffect.RequestTransfer
            viewModel.onEvent(AccountChooserEvent.OnDismiss)
            awaitItem() shouldBe AccountChooserEffect.Dismiss
        }
    }
})

private fun AccountChooserViewModel.content(): AccountChooserState.Content =
    value.shouldBeInstanceOf<AccountChooserState.Content>()

private fun newViewModel(
    repo: FakeAccountRepository,
    selectedId: AccountId? = null,
    excludeAccountId: AccountId? = null,
): AccountChooserViewModel = AccountChooserViewModel(
    initialSelectedId = selectedId,
    excludeAccountId = excludeAccountId,
    getAccounts = GetAccountsUseCase(
        repo,
        InMemorySessionPointers(currentWorkspaceId = workspaceId("ws-1")),
    ),
)

private class FakeAccountRepository : AccountRepository {
    private val accounts = MutableSharedFlow<List<Account>>(replay = 1, extraBufferCapacity = 4)
    private var current = emptyList<Account>()

    fun emit(value: List<Account>) {
        current = value
        check(accounts.tryEmit(value))
    }

    override fun getAll(): Flow<List<Account>> = accounts
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = accounts
    override suspend fun getById(id: AccountId): Account? = current.firstOrNull { it.id == id }
    override suspend fun insert(account: Account) = emit(current + account)
    override suspend fun update(account: Account) =
        emit(current.map { if (it.id == account.id) account else it })

    override suspend fun delete(id: AccountId) = emit(current.filterNot { it.id == id })
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}
