package com.georgeci.moneysurfer.feature.account.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountDetailsViewModel(
    accountId: AccountId,
    private val getAccountById: GetAccountByIdUseCase,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
) : MviViewModel<AccountDetailsState, AccountDetailsEvent, AccountDetailsEffect>(
    initialState = AccountDetailsState.Loading(accountId),
) {

    init {
        loadData()
    }

    override fun onEvent(event: AccountDetailsEvent) {
        when (event) {
            AccountDetailsEvent.OnBackClick -> postSideEffect(AccountDetailsEffect.NavigateBack)
            is AccountDetailsEvent.OnTransactionClick ->
                postSideEffect(AccountDetailsEffect.NavigateToTransactionDetails(event.transactionId))
            AccountDetailsEvent.OnAddTransactionClick ->
                postSideEffect(AccountDetailsEffect.NavigateToTransactionCreation)
            AccountDetailsEvent.OnEditClick ->
                postSideEffect(AccountDetailsEffect.NavigateToAccountEdit(currentState.accountId))
            AccountDetailsEvent.OnSeeAllTransactionsClick ->
                postSideEffect(AccountDetailsEffect.NavigateToTransactionsList(currentState.accountId))
            is AccountDetailsEvent.OnFilterChanged ->
                updateState {
                    AccountDetailsState.content.filter.modify(this) { event.filter }
                }
        }
    }

    private fun loadData() {
        launch {
            val accountId = currentState.accountId
            val account = getAccountById(accountId)
            val currency = account?.currencyCode ?: CurrencyCode("USD")
            getTransactionsByAccount(accountId).collect { transactions ->
                val visible = transactions.filter { it.type != TransactionType.OPENING_BALANCE }
                val income = visible.fold(Money.zero()) { acc, t ->
                    if (t.type == TransactionType.INCOME) acc + t.money.abs() else acc
                }
                val expenses = visible.fold(Money.zero()) { acc, t ->
                    if (t.type == TransactionType.EXPENSE) acc + t.money.abs() else acc
                }
                val txnUi = visible.map { it.toUi(currency) }
                val formattedIncome = MoneyFormatter.format(income, currency)
                val formattedExpenses = MoneyFormatter.format(expenses, currency)
                updateState {
                    when (this) {
                        is AccountDetailsState.Loading -> AccountDetailsState.Content(
                            accountId = accountId,
                            name = account?.name.orEmpty(),
                            formattedBalance = account?.let {
                                MoneyFormatter.format(it.balance, it.currencyCode)
                            }.orEmpty(),
                            currency = account?.currencyCode?.value.orEmpty(),
                            formattedIncome = formattedIncome,
                            formattedExpenses = formattedExpenses,
                            transactions = txnUi,
                            filter = TransactionFilter.All,
                        )
                        is AccountDetailsState.Content -> copy(
                            transactions = txnUi,
                            formattedIncome = formattedIncome,
                            formattedExpenses = formattedExpenses,
                        )
                    }
                }
            }
        }
    }

    private fun Transaction.toUi(currency: CurrencyCode) = AccountTransactionUi(
        id = id,
        title = note.ifBlank { "No description" },
        formattedAmount = MoneyFormatter.format(money.abs(), currency),
        isExpense = type == TransactionType.EXPENSE,
        categoryHueSeed = categoryId?.value.orEmpty(),
    )
}

@optics
sealed interface AccountDetailsState {
    val accountId: AccountId

    @optics
    data class Loading(override val accountId: AccountId) : AccountDetailsState {
        companion object
    }

    @optics
    data class Content(
        override val accountId: AccountId,
        val name: String,
        val formattedBalance: String,
        val currency: String,
        val formattedIncome: String,
        val formattedExpenses: String,
        val transactions: List<AccountTransactionUi>,
        val filter: TransactionFilter,
    ) : AccountDetailsState {
        companion object
    }

    companion object
}

enum class TransactionFilter { All, Expenses, Income }

data class AccountTransactionUi(
    val id: TransactionId,
    val title: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

sealed interface AccountDetailsEvent {
    data object OnBackClick : AccountDetailsEvent
    data object OnEditClick : AccountDetailsEvent
    data class OnTransactionClick(val transactionId: TransactionId) : AccountDetailsEvent
    data object OnAddTransactionClick : AccountDetailsEvent
    data object OnSeeAllTransactionsClick : AccountDetailsEvent
    data class OnFilterChanged(val filter: TransactionFilter) : AccountDetailsEvent
}

sealed interface AccountDetailsEffect {
    data object NavigateBack : AccountDetailsEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : AccountDetailsEffect
    data object NavigateToTransactionCreation : AccountDetailsEffect
    data class NavigateToAccountEdit(val accountId: AccountId) : AccountDetailsEffect
    data class NavigateToTransactionsList(val accountId: AccountId) : AccountDetailsEffect
}
