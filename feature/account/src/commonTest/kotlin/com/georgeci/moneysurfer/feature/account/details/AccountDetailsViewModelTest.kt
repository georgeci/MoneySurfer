package com.georgeci.moneysurfer.feature.account.details

import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
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
            val account = anAccount(extraDetails = details)
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
            val account = anAccount(extraDetails = details)
            val vm = viewModelFor(account, offline = true)
            try {
                vm.awaitContent().extraDetails shouldBe emptyList()
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

private fun viewModelFor(account: Account, offline: Boolean) = AccountDetailsViewModel(
    accountId = account.id,
    getAccountById = GetAccountByIdUseCase(SingleAccountRepository(account)),
    getTransactionsByAccount = GetTransactionsByAccountUseCase(NoTransactionsRepository()),
    offlineBuildFlags = OfflineBuildFlags(isOffline = offline),
)

/** Serves exactly one account; every other operation is out of this screen's reach. */
private class SingleAccountRepository(private val account: Account) : AccountRepository {
    override suspend fun getById(id: AccountId): Account? = account.takeIf { it.id == id }
    override fun getAll(): Flow<List<Account>> = MutableStateFlow(listOf(account))
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> =
        MutableStateFlow(listOf(account))
    override suspend fun insert(account: Account) = error("not used")
    override suspend fun update(account: Account) = error("not used")
    override suspend fun delete(id: AccountId) = error("not used")
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = error("not used")
    override suspend fun setBalance(accountId: AccountId, balance: Money) = error("not used")
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = error("not used")
}

private class NoTransactionsRepository : TransactionRepository {
    private val empty = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = empty
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = empty
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = empty
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ) = error("not used")
    override fun getTotals(accountId: AccountId?, window: TransactionPeriodWindow) = error("not used")
    override suspend fun getById(id: TransactionId): Transaction? = null
    override suspend fun insert(transaction: Transaction) = error("not used")
    override suspend fun update(transaction: Transaction) = error("not used")
    override suspend fun delete(id: TransactionId) = error("not used")
}
