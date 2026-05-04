package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
) : MviViewModel<DashboardState, DashboardEvent, DashboardEffect>(
    initialState = DashboardState.Loading,
) {

    init {
        observeDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.OnAccountClick -> postSideEffect(
                DashboardEffect.NavigateToAccountDetails(event.accountId),
            )
            is DashboardEvent.OnTransactionClick -> postSideEffect(
                DashboardEffect.NavigateToTransactionDetails(event.transactionId),
            )
            DashboardEvent.OnAddAccountClick -> postSideEffect(DashboardEffect.NavigateToAccountCreation)
            DashboardEvent.OnSeeAllTransactionsClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionsList)
            DashboardEvent.OnAddTransactionClick -> postSideEffect(
                DashboardEffect.NavigateToTransactionCreation(accountId = null),
            )
            is DashboardEvent.OnAddTransactionForAccountClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionCreation(accountId = event.accountId))
            DashboardEvent.OnManageAccountsClick -> postSideEffect(DashboardEffect.NavigateToAccountsManage)
            DashboardEvent.OnSettingsClick -> postSideEffect(DashboardEffect.NavigateToSettings)
        }
    }

    private fun observeDashboard() {
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
            ) { accounts, transactions ->
                DashboardState.Content(
                    accounts = accounts.map { it.toUi() },
                    transactions = transactions
                        .filter { it.type != TransactionType.OPENING_BALANCE }
                        .take(RECENT_TRANSACTIONS_LIMIT)
                        .map { it.toUi() },
                    formattedTotalBalance = accounts.formattedTotal(),
                    workspaceName = null,
                    workspaceInitial = null,
                    greeting = null,
                    formattedTrendDelta = null,
                )
            }.collect { newContent -> updateState { newContent } }
        }
    }

    private fun List<Account>.formattedTotal(): String? {
        val currency = firstOrNull()?.currencyCode ?: return null
        val total = filter { it.currencyCode == currency }
            .fold(Money.zero()) { acc, account -> acc + account.balance }
        return MoneyFormatter.format(total, currency)
    }

    private fun Account.toUi() = AccountUi(
        id = id,
        name = name,
        formattedBalance = MoneyFormatter.format(balance, currencyCode),
        currency = currencyCode.value,
    )

    private fun Transaction.toUi() = TransactionUi(
        id = id,
        title = note.ifBlank { "No description" },
        formattedAmount = MoneyFormatter.format(money.abs(), currencyCode),
        isExpense = type == TransactionType.EXPENSE,
        categoryHueSeed = categoryId?.value.orEmpty(),
    )
}

@optics
sealed interface DashboardState {
    data object Loading : DashboardState

    @optics
    data class Content(
        val accounts: List<AccountUi>,
        val transactions: List<TransactionUi>,
        val formattedTotalBalance: String?,
        val workspaceName: String?,
        val workspaceInitial: String?,
        val greeting: String?,
        val formattedTrendDelta: String?,
    ) : DashboardState {
        companion object
    }

    companion object
}

data class AccountUi(
    val id: AccountId,
    val name: String,
    val formattedBalance: String,
    val currency: String,
)

data class TransactionUi(
    val id: TransactionId,
    val title: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

sealed interface DashboardEvent {
    data class OnAccountClick(val accountId: AccountId) : DashboardEvent
    data class OnTransactionClick(val transactionId: TransactionId) : DashboardEvent
    data object OnAddAccountClick : DashboardEvent
    data object OnAddTransactionClick : DashboardEvent
    data object OnSeeAllTransactionsClick : DashboardEvent
    data class OnAddTransactionForAccountClick(val accountId: AccountId) : DashboardEvent
    data object OnManageAccountsClick : DashboardEvent
    data object OnSettingsClick : DashboardEvent
}

sealed interface DashboardEffect {
    data class NavigateToAccountDetails(val accountId: AccountId) : DashboardEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : DashboardEffect
    data object NavigateToAccountCreation : DashboardEffect
    data object NavigateToAccountsManage : DashboardEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : DashboardEffect
    data object NavigateToSettings : DashboardEffect
    data object NavigateToTransactionsList : DashboardEffect
}

private const val RECENT_TRANSACTIONS_LIMIT = 5
