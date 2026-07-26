package com.georgeci.moneysurfer.feature.account.details

import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountBalanceSeriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Extra details on the details screen — the only place the values collected during creation are
 * ever shown again — and the offline build's carve-out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailsViewModelTest : StringSpec({

    val details = listOf(
        AccountExtraDetail(key = "IBAN", value = "PL61 1090 1014"),
        AccountExtraDetail(key = "Broker code", value = "MS-4417"),
    )

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "the online build surfaces the account's extra details in the order they were saved" {
        runTest {
            val account = anAccount().copy(extraDetails = details)
            val vm = viewModelFor(account, offline = false)
            try {
                vm.awaitContent().extraDetails shouldBe details
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the offline build shows no extra details, matching the hidden creation section" {
        runTest {
            val account = anAccount().copy(extraDetails = details)
            val vm = viewModelFor(account, offline = true)
            try {
                vm.awaitContent().extraDetails shouldBe emptyList()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "income and expense totals sum only their own direction, ignoring the opening balance" {
        runTest {
            val account = anAccount(currencyCode = USD)
            val transactions = listOf(
                aTransaction(accountId = account.id, money = 30.dollars, type = TransactionType.INCOME),
                aTransaction(accountId = account.id, money = 70.dollars, type = TransactionType.INCOME),
                aTransaction(accountId = account.id, money = 25.dollars, type = TransactionType.EXPENSE),
                // Never counted on this screen — it is the account's starting point, not activity.
                aTransaction(
                    accountId = account.id,
                    money = 500.dollars,
                    type = TransactionType.OPENING_BALANCE,
                ),
            )
            val vm = viewModelFor(account, offline = false, transactions = transactions)
            try {
                val content = vm.awaitContent()
                content.formattedIncome shouldBe MoneyFormatter.format(100.dollars, USD)
                content.formattedExpenses shouldBe MoneyFormatter.format(25.dollars, USD)
                content.transactions.size shouldBe 3
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "swiping a transaction away removes it, and the snackbar's Undo puts it back" {
        runTest {
            val account = anAccount(currencyCode = USD)
            val snackbar = SnackbarController()
            val vm = viewModelFor(
                account = account,
                offline = false,
                transactions = listOf(
                    aTransaction(id = transactionId("keep"), accountId = account.id, money = 30.dollars),
                    aTransaction(id = transactionId("swiped"), accountId = account.id, money = 70.dollars),
                ),
                snackbar = snackbar,
            )
            try {
                vm.awaitContent()

                vm.onEvent(AccountDetailsEvent.OnDeleteTransaction(transactionId("swiped")))

                // Not an optimistic edit of the state — the row is gone because the query re-emitted.
                vm.awaitContent().transactions.map { it.id.value } shouldBe listOf("keep")

                snackbar.requests.first().onAction!!.invoke()

                vm.awaitContent().transactions.map { it.id.value } shouldBe listOf("keep", "swiped")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an account without extra details reports an empty list rather than a blank section" {
        runTest {
            val vm = viewModelFor(anAccount(), offline = false)
            try {
                vm.awaitContent().extraDetails shouldBe emptyList()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private suspend fun AccountDetailsViewModel.awaitContent(): AccountDetailsState.Content =
    first { it is AccountDetailsState.Content } as AccountDetailsState.Content

private fun viewModelFor(
    account: Account,
    offline: Boolean,
    transactions: List<Transaction> = emptyList(),
    snackbar: SnackbarController = SnackbarController(),
): AccountDetailsViewModel {
    val accounts = SingleAccountRepository(account)
    val txns = FixedTransactionRepository(transactions)
    val applyChange = ApplyTransactionChangeUseCase(txns, accounts)
    return AccountDetailsViewModel(
        accountId = account.id,
        getAccountById = GetAccountByIdUseCase(accounts),
        getTransactionsByAccount = GetTransactionsByAccountUseCase(txns),
        getAccountBalanceSeries = GetAccountBalanceSeriesUseCase(ClockUseCase()),
        hostCapabilities = FakeHostCapabilities(isOffline = offline),
        deleteWithUndo = DeleteTransactionWithUndo(
            deleteTransaction = DeleteTransactionUseCase(txns, applyChange),
            restoreTransactions = RestoreTransactionsUseCase(applyChange),
            snackbar = snackbar,
        ),
    )
}

/** Serves exactly one account; every other operation is out of this screen's reach. */
private class SingleAccountRepository(private val account: Account) : AccountRepository {
    override suspend fun getById(id: AccountId): Account? = account.takeIf { it.id == id }
    override fun getAll(): Flow<List<Account>> = MutableStateFlow(listOf(account))
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> =
        MutableStateFlow(listOf(account))
    override suspend fun insert(account: Account) = error("not used")
    override suspend fun update(account: Account) = error("not used")
    override suspend fun delete(id: AccountId) = error("not used")

    // Deleting a transaction moves the balance, so the write has to land somewhere. What the
    // balance becomes is asserted in DeleteUndoIntegrationIT against real Room.
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = error("not used")
    override suspend fun reorder(orderedIds: List<AccountId>) = error("not used")
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = error("not used")
}

private class FixedTransactionRepository(transactions: List<Transaction>) : TransactionRepository {
    private val all = MutableStateFlow(transactions)

    override fun getAll(): Flow<List<Transaction>> = all
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = all
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = all
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ) = error("not used")
    override fun getTotals(accountId: AccountId?, window: TransactionPeriodWindow) = error("not used")
    override suspend fun getById(id: TransactionId): Transaction? = all.value.find { it.id == id }
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        all.value.filter { it.transferId == transferId }

    // Writing, not no-op: the list is fed by this flow, so a delete has to actually leave the rows
    // for the screen's reaction to it to be worth asserting.
    override suspend fun insert(transaction: Transaction) {
        all.value = all.value + transaction
    }

    override suspend fun update(transaction: Transaction) {
        all.value = all.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(id: TransactionId) {
        all.value = all.value.filterNot { it.id == id }
    }
}
