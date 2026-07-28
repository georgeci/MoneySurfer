package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.insight.Insight
import com.georgeci.moneysurfer.domain.insight.InsightTone
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.TransactionSplitGroup
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSafeToSpendUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val getSafeToSpend: GetSafeToSpendUseCase,
    private val generateInsights: GenerateInsightsUseCase,
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

    /**
     * The span the spend-oriented widgets are read at. Device-local and not persisted, per
     * `md/insights.md` decision 6 — the dashboard opens on [DashboardPeriod.DEFAULT] every launch.
     */
    private val period = MutableStateFlow(DashboardPeriod.DEFAULT)

    init {
        observeDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.Navigate -> postSideEffect(event.destination())
            is DashboardEvent.OnPeriodChange -> period.value = event.period
        }
    }

    private fun observeDashboard() {
        // Layout, period and safe-to-spend are folded into one source because `combine` tops out
        // at five typed flows, and these three are the group that is never read apart.
        //
        // The period reaches safe-to-spend as a flow, not as this combine's value: `getSafeToSpend`
        // re-picks the budget in place that way, instead of restarting the workspace-wide
        // transaction query on every tap of the switch. The cost is one intermediate emission on a
        // change — the new period beside the previous budget's figures — which resolves on the next
        // frame and only differs at all when the workspace runs budgets on both cadences.
        val screenChrome = combine(
            layout,
            period,
            getSafeToSpend(period.map { it.budgetPeriod }).onStart { emit(null) },
        ) { layoutConfig, selectedPeriod, safeToSpend ->
            Triple(layoutConfig, selectedPeriod, safeToSpend)
        }
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
                getGoals(),
                getExchangeRates().onStart { emit(null) },
                screenChrome,
            ) { accounts, transactions, goals, rates, (layoutConfig, selectedPeriod, safeToSpend) ->
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
                    safeToSpend = safeToSpend?.toUi(),
                    isOffline = isOffline,
                    transferEnabled = transferEnabled,
                    layout = layoutConfig,
                    period = selectedPeriod,
                )
            }
                // Joined here rather than folded into a pair above: the insights are the slowest
                // input — two aggregates plus the category tree — so keeping them last lets the
                // rest of the dashboard draw without waiting on them.
                .combine(generateInsights().onStart { emit(emptyList()) }) { content, insights ->
                    content.copy(insights = insights.map { it.toUi() })
                }
                .collect { newContent -> updateState { newContent } }
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

    private fun SavingsGoalSummary.toUi() = GoalUi(
        id = goal.id,
        name = goal.title,
        formattedSaved = MoneyFormatter.format(progress.saved, goal.currencyCode),
        formattedTarget = MoneyFormatter.format(progress.target, goal.currencyCode),
        progress = progress.percent.toFloat(),
    )

    /**
     * Money is formatted here and the sentence is assembled in the composable: the amounts need a
     * currency the screen does not carry, and the copy needs a locale the ViewModel does not.
     */
    private fun Insight.toUi(): InsightUi = when (this) {
        is Insight.CategoryChange -> InsightUi(
            id = id,
            kind = if (isIncrease) InsightKind.CategoryUp else InsightKind.CategoryDown,
            tone = tone,
            label = categoryName,
            amount = MoneyFormatter.format(current, currency),
            comparison = MoneyFormatter.format(previous, currency),
            percent = changePercent,
        )
        is Insight.PeriodSpend -> InsightUi(
            id = id,
            kind = when (trend) {
                SpendTrend.Up -> InsightKind.PeriodUp
                SpendTrend.Down -> InsightKind.PeriodDown
                SpendTrend.Flat -> InsightKind.PeriodFlat
            },
            tone = tone,
            amount = MoneyFormatter.format(current, currency),
            comparison = MoneyFormatter.format(previous, currency),
            percent = changePercent,
        )
        is Insight.ActiveSubscriptions -> InsightUi(
            id = id,
            kind = InsightKind.Subscriptions,
            tone = tone,
            amount = MoneyFormatter.format(monthlyTotal, currency),
            count = count,
        )
    }

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
        /** Generated spending insights, most actionable first. Empty until the engine has run. */
        val insights: List<InsightUi> = emptyList(),
        val isOffline: Boolean = false,
        /**
         * Whether this build offers multi-account transfers. The quick-actions widget is the only
         * thing that reads it: half of that widget is a Transfer button, and a build with transfers
         * off has no form to send it to.
         */
        val transferEnabled: Boolean = false,
        /** Which widgets the screen renders, in order. */
        val layout: DashboardLayoutConfig = DashboardLayoutConfig.DEFAULT,
        /**
         * The span every spend-oriented widget reads at once. One value for the whole screen, so
         * two widgets can never disagree about which days they are summarising.
         */
        val period: DashboardPeriod = DashboardPeriod.DEFAULT,
    ) : DashboardState {

        /**
         * Single source of truth for the "no transactions logged yet" decision so screens
         * never re-derive it — keeps the empty-state vs. content choice off the composable.
         */
        val recentTransactionsEmpty: Boolean
            get() = transactions.isEmpty()

        /** Whether the period switch has anything under it to drive — see `hasPeriodScopedWidget`. */
        val periodSwitchVisible: Boolean
            get() = layout.hasPeriodScopedWidget

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
 * Which sentence an insight draws. One entry per rule outcome rather than per rule, because the
 * direction is what changes the copy: "Dining is up 24%" and "Dining is down 24%" are two
 * sentences, not one with a sign in it.
 */
enum class InsightKind {
    CategoryUp,
    CategoryDown,
    PeriodUp,
    PeriodDown,
    PeriodFlat,
    Subscriptions,
}

/**
 * One insight ready for the widget: which sentence to draw ([kind] and [tone]) plus the values
 * that fill its placeholders, already formatted in the workspace base currency.
 *
 * Flat rather than a mirror of the domain's sealed `Insight`: every insight draws the same card,
 * so a per-kind UI type would duplicate the domain hierarchy to feed one `when`. Fields a kind
 * does not use keep their defaults and never reach its copy.
 */
data class InsightUi(
    val id: String,
    val kind: InsightKind,
    val tone: InsightTone,
    /** Category name for the two category kinds; null for the uncategorized bucket. */
    val label: String? = null,
    /** The headline amount — this period's spend, or the monthly subscription total. */
    val amount: String = "",
    /** The same-stretch figure from the previous period. Unused by [InsightKind.Subscriptions]. */
    val comparison: String = "",
    /** Magnitude of the change in whole percent. Zero for the kinds that compare nothing. */
    val percent: Int = 0,
    /** Active subscription count. Zero for every kind but [InsightKind.Subscriptions]. */
    val count: Int = 0,
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

/**
 * The navigation half of the dashboard's contract as a table. A file-level function rather than a
 * method on the view model for the same reason `DashboardNavigation.navigate` is one (issue #404):
 * it is a dozen one-line branches where swapping two lines still compiles, still passes every other
 * test, and lands the user on the wrong screen — so it has to be drivable by a plain spec with no
 * view model to build. Between the two tables, event → effect → destination is covered end to end.
 *
 * Split out of `onEvent` because the dashboard is a hub: thirteen of its fourteen events do nothing
 * but name a destination, and leaving them inline left the one event that *changes this screen* as
 * the fourteenth line of a list that reads as boilerplate.
 */
internal fun DashboardEvent.Navigate.destination(): DashboardEffect = when (this) {
    is DashboardEvent.OnAccountClick -> DashboardEffect.NavigateToAccountDetails(accountId)
    is DashboardEvent.OnTransactionClick -> DashboardEffect.NavigateToTransactionDetails(transactionId)
    DashboardEvent.OnAddAccountClick -> DashboardEffect.NavigateToAccountCreation
    DashboardEvent.OnSeeAllTransactionsClick -> DashboardEffect.NavigateToTransactionsList
    DashboardEvent.OnAddTransactionClick -> DashboardEffect.NavigateToTransactionCreation(accountId = null)
    DashboardEvent.OnTransferClick -> DashboardEffect.NavigateToTransferCreation
    is DashboardEvent.OnAddTransactionForAccountClick ->
        DashboardEffect.NavigateToTransactionCreation(accountId = accountId)
    DashboardEvent.OnManageAccountsClick -> DashboardEffect.NavigateToAccountsManage
    DashboardEvent.OnSettingsClick -> DashboardEffect.NavigateToSettings
    DashboardEvent.OnCustomizeClick -> DashboardEffect.NavigateToCustomize
    DashboardEvent.OnSeeAllGoalsClick -> DashboardEffect.NavigateToGoals
    is DashboardEvent.OnGoalClick -> DashboardEffect.NavigateToGoalDetails(goalId)
    DashboardEvent.OnSetBudgetClick -> DashboardEffect.NavigateToBudgetCreation
}

sealed interface DashboardEvent {

    /**
     * An event whose whole answer is a destination — the dashboard is a hub, so nearly all of
     * them are. Declaring that as a type rather than as a convention keeps the mapping in
     * `destination()` exhaustive: an event added here without a branch there is a compile error,
     * and one that belongs on this screen instead simply does not extend this.
     */
    sealed interface Navigate : DashboardEvent

    data class OnAccountClick(val accountId: AccountId) : Navigate
    data class OnTransactionClick(val transactionId: TransactionId) : Navigate
    data object OnAddAccountClick : Navigate
    data object OnAddTransactionClick : Navigate
    data object OnTransferClick : Navigate
    data object OnSeeAllTransactionsClick : Navigate
    data class OnAddTransactionForAccountClick(val accountId: AccountId) : Navigate
    data object OnManageAccountsClick : Navigate
    data object OnSettingsClick : Navigate
    data object OnCustomizeClick : Navigate
    data object OnSeeAllGoalsClick : Navigate
    data class OnGoalClick(val goalId: GoalId) : Navigate
    data object OnSetBudgetClick : Navigate

    /** The Week/Month switch. Affects the screen only — nothing about it is written to disk. */
    data class OnPeriodChange(val period: DashboardPeriod) : DashboardEvent
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
}

private const val RECENT_TRANSACTIONS_LIMIT = 5

/** The widget shows two rows at most; reading more of the list would be wasted work. */
private const val DASHBOARD_GOALS_LIMIT = 2
