package com.georgeci.moneysurfer.feature.account.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountBalanceSeries
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountBalanceSeriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountDetailsViewModel(
    accountId: AccountId,
    private val getAccountById: GetAccountByIdUseCase,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
    private val getAccountBalanceSeries: GetAccountBalanceSeriesUseCase,
    private val offlineBuildFlags: OfflineBuildFlags,
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
                val totals = visible.formatTotals(currency)
                val txnUi = visible.map { it.toUi(currency) }
                // The chart is anchored on the same balance the hero card prints, and folds the
                // history already collected above rather than issuing a second query.
                val series = getAccountBalanceSeries(
                    accountId = accountId,
                    currentBalance = account?.balance ?: Money.zero(),
                    transactions = transactions,
                )
                val chart = series.toUi(currency)
                updateState {
                    when (this) {
                        is AccountDetailsState.Loading ->
                            account.toContent(accountId, txnUi, totals, chart)
                        is AccountDetailsState.Content -> copy(
                            transactions = txnUi,
                            formattedIncome = totals.income,
                            formattedExpenses = totals.expenses,
                            chart = chart,
                        )
                    }
                }
            }
        }
    }

    /** Per-direction sums over the visible transactions, already formatted for display. */
    private fun List<Transaction>.formatTotals(currency: CurrencyCode): FormattedTotals {
        val income = sumOf(TransactionType.INCOME)
        val expenses = sumOf(TransactionType.EXPENSE)
        return FormattedTotals(
            income = MoneyFormatter.format(income, currency),
            expenses = MoneyFormatter.format(expenses, currency),
        )
    }

    private fun List<Transaction>.sumOf(type: TransactionType): Money =
        fold(Money.zero()) { acc, t -> if (t.type == type) acc + t.money.abs() else acc }

    /** First resolved state for the screen. A null account means it was deleted underneath us. */
    private fun Account?.toContent(
        accountId: AccountId,
        transactions: List<AccountTransactionUi>,
        totals: FormattedTotals,
        chart: AccountBalanceChartUi,
    ) = AccountDetailsState.Content(
        accountId = accountId,
        name = this?.name.orEmpty(),
        formattedBalance = this?.let { MoneyFormatter.format(it.balance, it.currencyCode) }.orEmpty(),
        type = this?.type,
        formattedIncome = totals.income,
        formattedExpenses = totals.expenses,
        chart = chart,
        transactions = transactions,
        filter = TransactionFilter.All,
        // Mirrors the creation screen: the offline build has no place to put these, so it does
        // not offer to collect them and does not show them.
        extraDetails = if (offlineBuildFlags.isOffline) emptyList() else this?.extraDetails.orEmpty(),
    )

    /**
     * The card scales its own axes, so the y values stay in major units and x is just the day
     * index — the series is evenly spaced by construction.
     */
    private fun AccountBalanceSeries.toUi(currency: CurrencyCode): AccountBalanceChartUi {
        val formatted = MoneyFormatter.format(delta, currency)
        return AccountBalanceChartUi(
            points = points.mapIndexed { index, point ->
                index.toFloat() to (point.balance.minor / Money.MINOR_PER_MAJOR).toFloat()
            },
            // Locale formatting only signs negatives; a flat period reads as "+" too, since
            // "no change" is not a loss.
            formattedDelta = if (delta.isNegative()) formatted else "+$formatted",
            isDeltaNegative = delta.isNegative(),
        )
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
        /** Drives the hero meta line. Null only while the account row is still being read. */
        val type: AccountType?,
        val formattedIncome: String,
        val formattedExpenses: String,
        val chart: AccountBalanceChartUi,
        val transactions: List<AccountTransactionUi>,
        val filter: TransactionFilter,
        /** User-entered key–value details; empty when there are none or in the offline build. */
        val extraDetails: List<AccountExtraDetail> = emptyList(),
    ) : AccountDetailsState {
        companion object
    }

    companion object
}

/** Display-ready income/expense totals for the quick-stats row. */
private data class FormattedTotals(val income: String, val expenses: String)

enum class TransactionFilter { All, Expenses, Income }

/** Balance curve for the details chart: `x to y` pairs the card plots, plus its signed delta. */
data class AccountBalanceChartUi(
    val points: List<Pair<Float, Float>>,
    val formattedDelta: String,
    val isDeltaNegative: Boolean,
)

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
