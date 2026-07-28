package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.BudgetProgress
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.TransactionSplitGroup
import com.georgeci.moneysurfer.domain.model.safeToSpend
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetActiveBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Instant

@KoinViewModel
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
    private val getGoals: GetGoalsUseCase,
    private val getExchangeRates: GetExchangeRatesUseCase,
    private val getActiveBudgetProgress: GetActiveBudgetProgressUseCase,
    private val convertAccountsTotal: ConvertAccountsTotalUseCase,
    uiPreferences: UiPreferences,
    hostCapabilities: HostCapabilities,
) : MviViewModel<DashboardState, DashboardEvent, DashboardEffect>(
    initialState = DashboardState.Loading,
) {

    private val isOffline: Boolean = hostCapabilities.isOffline

    private val transferEnabled: Boolean = hostCapabilities.transferEnabled

    /**
     * Widget order and visibility. Normalized again here so the screen renders a sound layout
     * whatever supplies the pref — the DataStore-backed binding already normalizes on decode, but
     * an in-memory or future remote one need not, and `normalized()` is idempotent.
     */
    private val layout = uiPreferences.dashboardLayout.flow
        .map { it.normalized() }
        .onStart { emit(DashboardLayoutConfig.DEFAULT) }

    init {
        observeDashboard()
    }

    // One branch per widget interaction, nearly all of them a single `postSideEffect`. The count
    // grows with the number of widgets on the dashboard rather than with any logic here.
    @Suppress("CyclomaticComplexMethod")
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
            DashboardEvent.OnTransferClick -> postSideEffect(DashboardEffect.NavigateToTransferCreation)
            is DashboardEvent.OnAddTransactionForAccountClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionCreation(accountId = event.accountId))
            DashboardEvent.OnManageAccountsClick -> postSideEffect(DashboardEffect.NavigateToAccountsManage)
            DashboardEvent.OnSettingsClick -> postSideEffect(DashboardEffect.NavigateToSettings)
            DashboardEvent.OnCustomizeClick -> postSideEffect(DashboardEffect.NavigateToCustomize)
            DashboardEvent.OnSeeAllGoalsClick -> postSideEffect(DashboardEffect.NavigateToGoals)
            is DashboardEvent.OnGoalClick -> postSideEffect(DashboardEffect.NavigateToGoalDetails(event.goalId))
            DashboardEvent.OnSetBudgetClick -> postSideEffect(DashboardEffect.NavigateToBudgetCreation)
            DashboardEvent.OnSeeAllBudgetsClick -> postSideEffect(DashboardEffect.NavigateToBudgets)
            is DashboardEvent.OnBudgetClick -> postSideEffect(
                DashboardEffect.NavigateToBudgetDetails(event.budgetId),
            )
        }
    }

    private fun observeDashboard() {
        // Layout and budget progress are folded into one source because `combine` tops out at five
        // typed flows, and these two are the pair that is never read apart.
        //
        // One budget subscription feeds two widgets: safe-to-spend is a projection of the same
        // progress list the budgets widget renders, so the headline and the rows can never disagree
        // about a budget — and reading them apart would compute every budget's progress twice.
        val layoutAndBudgets =
            combine(layout, getActiveBudgetProgress().onStart { emit(emptyList()) }) { layoutConfig, progress ->
                layoutConfig to progress
            }
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
                getGoals(),
                getExchangeRates().onStart { emit(null) },
                layoutAndBudgets,
            ) { accounts, transactions, goals, rates, (layoutConfig, budgetProgress) ->
                val balance = accounts.convertedTotal(rates)?.toBalanceUi()
                DashboardState.Content(
                    accounts = accounts.map { it.toUi() },
                    transactions = transactions
                        .filter { it.primary.type != TransactionType.OPENING_BALANCE }
                        .take(RECENT_TRANSACTIONS_LIMIT)
                        .map { it.toUi() },
                    formattedTotalBalance = balance?.headline,
                    otherCurrencyTotals = balance?.notConverted.orEmpty(),
                    ratesAsOf = balance?.asOf,
                    workspaceName = null,
                    workspaceInitial = null,
                    greeting = null,
                    formattedTrendDelta = null,
                    goals = goals.take(DASHBOARD_GOALS_LIMIT).map { it.toUi() },
                    safeToSpend = budgetProgress.safeToSpend()?.toUi(),
                    budgets = budgetProgress.toBudgetsUi(),
                    isOffline = isOffline,
                    transferEnabled = transferEnabled,
                    layout = layoutConfig,
                )
            }.collect { newContent -> updateState { newContent } }
        }
    }

    /**
     * The converted total. `null` until a base currency is known — a workspace is always selected
     * by the time accounts exist, so this only covers the first frame after launch and the
     * signed-out state, both of which render the empty balance anyway.
     */
    private fun List<Account>.convertedTotal(rates: ExchangeRateSnapshot?): ConvertedTotal? =
        rates?.let { convertAccountsTotal(this, it.baseCurrency, it.rates) }

    /**
     * Splits the total into what the balance widget renders.
     *
     * When nothing could be priced in the base currency — a workspace whose accounts are all in
     * currencies the cache does not cover — the headline falls back to the largest remaining
     * bucket instead of going empty. `formattedTotalBalance == null` is the screen's "no accounts
     * at all" signal, and letting an unconvertible balance trip it would tell a user who *has*
     * money to add their first account.
     */
    private fun ConvertedTotal.toBalanceUi(): BalanceUi {
        val leftOver = unconverted.map { MoneyFormatter.format(it.amount, it.currencyCode) }
        return when (val converted = total) {
            null -> BalanceUi(
                headline = leftOver.firstOrNull(),
                notConverted = leftOver.drop(1),
                asOf = null,
            )
            else -> BalanceUi(
                headline = MoneyFormatter.format(converted, baseCurrency),
                notConverted = leftOver,
                asOf = asOf?.asIsoDate(),
            )
        }
    }

    /** ISO date — the "as of" label has to be unambiguous, and the app ships no date locale rules. */
    private fun Instant.asIsoDate(): String =
        toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private fun Account.toUi() = AccountUi(
        id = id,
        name = name,
        formattedBalance = MoneyFormatter.format(balance, currencyCode),
        currency = currencyCode.value,
    )

    /**
     * The headline is signed — negative once the budget is overspent — so the widget states the
     * overshoot rather than flooring at zero and pretending the period is merely spent out.
     */
    private fun SafeToSpend.toUi(): SafeToSpendUi = SafeToSpendUi(
        budgetName = budgetName,
        remainingFormatted = MoneyFormatter.format(remaining, currency),
        spentFormatted = MoneyFormatter.format(spent, currency),
        limitFormatted = MoneyFormatter.format(limit, currency),
        perDayFormatted = MoneyFormatter.format(perDay, currency),
        daysLeft = daysLeft,
        progress = spentFraction,
        paceFraction = elapsedFraction,
        status = status,
    )

    /**
     * The budgets the widget lists, most pressing first: spend against the limit, descending, with
     * ties broken by budget id so two equally-spent budgets keep a stable order across emissions.
     * A budget the user is closest to blowing is the one worth the row — the rest are a tap away on
     * the budgets screen.
     *
     * Progress carrying no base currency is dropped rather than formatted against a guess: the
     * workspace row can be missing behind its budgets for a moment after a pull, and
     * `MoneyFormatter` is backed by `java.util.Currency`, which rejects anything but an ISO code.
     */
    private fun List<BudgetProgress>.toBudgetsUi(): List<BudgetSummaryUi> =
        sortedWith(compareByDescending<BudgetProgress> { it.spentFraction }.thenBy { it.budget.id.value })
            .mapNotNull { it.toUiOrNull() }
            .take(DASHBOARD_BUDGETS_LIMIT)

    private fun BudgetProgress.toUiOrNull(): BudgetSummaryUi? {
        val currency = currency ?: return null
        return BudgetSummaryUi(
            id = budget.id,
            name = budget.name,
            spentFormatted = MoneyFormatter.format(spent, currency),
            limitFormatted = MoneyFormatter.format(effectiveLimit, currency),
            remainderFormatted = MoneyFormatter.format(remaining.abs(), currency),
            progress = spentFraction,
            alertFraction = budget.alertPercent / PERCENT,
            status = status,
        )
    }

    private fun SavingsGoalSummary.toUi() = GoalUi(
        id = goal.id,
        name = goal.title,
        formattedSaved = MoneyFormatter.format(progress.saved, goal.currencyCode),
        formattedTarget = MoneyFormatter.format(progress.target, goal.currencyCode),
        progress = progress.percent.toFloat(),
    )

    /**
     * One receipt as the widget draws it. A split shows the whole payment and how many categories
     * it covers — its legs are separate rows only where a category is what the screen is about.
     */
    private fun TransactionSplitGroup.toUi() = TransactionUi(
        id = primary.id,
        title = primary.note.ifBlank { "No description" },
        formattedAmount = MoneyFormatter.format(total, primary.currencyCode),
        isExpense = primary.type == TransactionType.EXPENSE,
        categoryHueSeed = primary.categoryId?.value.orEmpty(),
        splitCategoryCount = if (isSplit) categoryCount else 0,
    )
}

@optics
sealed interface DashboardState {
    data object Loading : DashboardState

    @optics
    data class Content(
        val accounts: List<AccountUi>,
        val transactions: List<TransactionUi>,
        /**
         * Every account balance converted into the workspace base currency, or null when no
         * account could be priced there (no accounts at all, or none in a currency the cached
         * rates cover).
         */
        val formattedTotalBalance: String?,
        /**
         * Per-currency totals for the accounts no cached rate could convert. Shown as a note
         * beside the headline — dropping them was the old bug, and they are never folded into
         * the headline at a rate the app does not have.
         */
        val otherCurrencyTotals: List<String> = emptyList(),
        /**
         * ISO date the converting rates were published, or null when the total needed no rates.
         * Renders as the staleness signal so a total computed offline says how old it is.
         */
        val ratesAsOf: String? = null,
        val workspaceName: String?,
        val workspaceInitial: String?,
        val greeting: String?,
        val formattedTrendDelta: String?,
        val goals: List<GoalUi> = emptyList(),
        /** What is still safe to spend this period, or null while no active budget backs a number. */
        val safeToSpend: SafeToSpendUi? = null,
        /** The active budgets worth a row, most pressing first — empty when none are tracked. */
        val budgets: List<BudgetSummaryUi> = emptyList(),
        val isOffline: Boolean = false,
        /**
         * Whether this build offers multi-account transfers. The quick-actions widget is the only
         * thing that reads it: half of that widget is a Transfer button, and a build with transfers
         * off has no form to send it to.
         */
        val transferEnabled: Boolean = false,
        /** Which widgets the screen renders, in order. */
        val layout: DashboardLayoutConfig = DashboardLayoutConfig.DEFAULT,
    ) : DashboardState {

        /**
         * Single source of truth for the "no transactions logged yet" decision so screens
         * never re-derive it — keeps the empty-state vs. content choice off the composable.
         */
        val recentTransactionsEmpty: Boolean
            get() = transactions.isEmpty()

        companion object
    }

    companion object
}

/** The three strings the balance widget needs, already formatted. */
private data class BalanceUi(
    val headline: String?,
    val notConverted: List<String>,
    val asOf: String?,
)

data class AccountUi(
    val id: AccountId,
    val name: String,
    val formattedBalance: String,
    val currency: String,
)

data class GoalUi(
    val id: GoalId,
    val name: String,
    val formattedSaved: String,
    val formattedTarget: String,
    val progress: Float,
)

/**
 * The safe-to-spend widget's numbers. Money is formatted here; the sentences around it are built
 * on the screen, which is where the string resources are.
 */
data class SafeToSpendUi(
    val budgetName: String,
    /** Signed — negative once the budget is overspent. */
    val remainingFormatted: String,
    val spentFormatted: String,
    val limitFormatted: String,
    val perDayFormatted: String,
    val daysLeft: Int,
    /** Spend against the limit; can exceed 1, which the bar caps rather than overdraws. */
    val progress: Float,
    /** How much of the period has gone — the tick [progress] is read against. */
    val paceFraction: Float,
    val status: BudgetStatus,
) {
    /** Derived rather than stored, so the wording and the colour can never disagree. */
    val isOver: Boolean get() = status == BudgetStatus.OVER
}

/**
 * One budget row of the budgets widget. Money is formatted here; the sentences around it — the
 * status word, "left" against "over" — are built on the screen, which is where the strings are.
 */
data class BudgetSummaryUi(
    val id: BudgetId,
    val name: String,
    val spentFormatted: String,
    /** The budget amount plus any rollover carry — what the spend is measured against. */
    val limitFormatted: String,
    /** Always positive — the sign lives in [isOver], which picks the wording. */
    val remainderFormatted: String,
    /** Spend against the limit; can exceed 1, which the bar caps rather than overdraws. */
    val progress: Float,
    /** Where this budget's alert threshold sits — the tick [progress] is read against. */
    val alertFraction: Float,
    val status: BudgetStatus,
) {
    /** Derived rather than stored, so the wording and the colour can never disagree. */
    val isOver: Boolean get() = status == BudgetStatus.OVER
}

data class TransactionUi(
    val id: TransactionId,
    val title: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
    /**
     * How many categories a collapsed split covers, or `0` for an ordinary transaction. Non-zero
     * makes [formattedAmount] the whole receipt and gives the row its "N categories" meta line;
     * [id] is the leg tapping it opens.
     */
    val splitCategoryCount: Int = 0,
)

sealed interface DashboardEvent {
    data class OnAccountClick(val accountId: AccountId) : DashboardEvent
    data class OnTransactionClick(val transactionId: TransactionId) : DashboardEvent
    data object OnAddAccountClick : DashboardEvent
    data object OnAddTransactionClick : DashboardEvent
    data object OnTransferClick : DashboardEvent
    data object OnSeeAllTransactionsClick : DashboardEvent
    data class OnAddTransactionForAccountClick(val accountId: AccountId) : DashboardEvent
    data object OnManageAccountsClick : DashboardEvent
    data object OnSettingsClick : DashboardEvent
    data object OnCustomizeClick : DashboardEvent
    data object OnSeeAllGoalsClick : DashboardEvent
    data class OnGoalClick(val goalId: GoalId) : DashboardEvent
    data object OnSetBudgetClick : DashboardEvent
    data object OnSeeAllBudgetsClick : DashboardEvent
    data class OnBudgetClick(val budgetId: BudgetId) : DashboardEvent
}

sealed interface DashboardEffect {
    data class NavigateToAccountDetails(val accountId: AccountId) : DashboardEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : DashboardEffect
    data object NavigateToAccountCreation : DashboardEffect
    data object NavigateToAccountsManage : DashboardEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : DashboardEffect

    /** The same creation screen, opened on its Transfer tab rather than the default expense one. */
    data object NavigateToTransferCreation : DashboardEffect
    data object NavigateToSettings : DashboardEffect
    data object NavigateToCustomize : DashboardEffect
    data object NavigateToTransactionsList : DashboardEffect
    data object NavigateToGoals : DashboardEffect
    data class NavigateToGoalDetails(val goalId: GoalId) : DashboardEffect

    /** The budget editor, opened empty — the way out of the safe-to-spend widget's empty state. */
    data object NavigateToBudgetCreation : DashboardEffect
    data object NavigateToBudgets : DashboardEffect
    data class NavigateToBudgetDetails(val budgetId: BudgetId) : DashboardEffect
}

private const val RECENT_TRANSACTIONS_LIMIT = 5

/** The widget shows two rows at most; reading more of the list would be wasted work. */
private const val DASHBOARD_GOALS_LIMIT = 2

/** Three rows is what the full-size budgets card draws; the compact one keeps the first. */
private const val DASHBOARD_BUDGETS_LIMIT = 3

/** Alert thresholds are stored as whole percents; the bar wants a fraction. */
private const val PERCENT = 100f
