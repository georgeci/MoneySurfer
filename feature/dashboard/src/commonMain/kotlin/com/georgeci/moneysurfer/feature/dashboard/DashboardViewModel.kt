package com.georgeci.moneysurfer.feature.dashboard

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.insight.Insight
import com.georgeci.moneysurfer.domain.insight.InsightTone
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.BurnRate
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.TransactionSplitGroup
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBurnRateUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSafeToSpendUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Instant

@KoinViewModel
// One injected collaborator per widget that reads its own data, which is what a dashboard is;
// the tenth arrived when the burn-rate and insights widgets landed together. Bundling them into a
// holder would buy a shorter signature and cost a layer of indirection through the Koin graph —
// the same trade `AppLaunchViewModel` declined.
@Suppress("LongParameterList")
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
    private val getGoals: GetGoalsUseCase,
    private val getExchangeRates: GetExchangeRatesUseCase,
    private val getSafeToSpend: GetSafeToSpendUseCase,
    private val getBurnRate: GetBurnRateUseCase,
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
            DashboardEvent.OnTransferClick -> postSideEffect(DashboardEffect.NavigateToTransferCreation)
            is DashboardEvent.OnAddTransactionForAccountClick ->
                postSideEffect(DashboardEffect.NavigateToTransactionCreation(accountId = event.accountId))
            DashboardEvent.OnManageAccountsClick -> postSideEffect(DashboardEffect.NavigateToAccountsManage)
            DashboardEvent.OnSettingsClick -> postSideEffect(DashboardEffect.NavigateToSettings)
            DashboardEvent.OnCustomizeClick -> postSideEffect(DashboardEffect.NavigateToCustomize)
            DashboardEvent.OnSeeAllGoalsClick -> postSideEffect(DashboardEffect.NavigateToGoals)
            is DashboardEvent.OnGoalClick -> postSideEffect(DashboardEffect.NavigateToGoalDetails(event.goalId))
            DashboardEvent.OnSetBudgetClick -> postSideEffect(DashboardEffect.NavigateToBudgetCreation)
        }
    }

    private fun observeDashboard() {
        // The layout and the two budget-shaped widgets are folded into one source because `combine`
        // tops out at five typed flows, and these are never read apart from each other.
        val widgetSources = combine(
            layout,
            getSafeToSpend().onStart { emit(null) },
            getBurnRate().onStart { emit(null) },
            ::WidgetSources,
        )
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
                getGoals(),
                getExchangeRates().onStart { emit(null) },
                widgetSources,
            ) { accounts, transactions, goals, rates, (layoutConfig, safeToSpend, burnRate) ->
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
                    burnRate = burnRate?.toUi(),
                    isOffline = isOffline,
                    transferEnabled = transferEnabled,
                    layout = layoutConfig,
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

    /**
     * The chart's bars carry no money of their own: a currency printed seven times over a 64dp-tall
     * column is unreadable, so the amounts live in the headline, the callout and the chart's
     * screen-reader line, and each bar keeps only its height and its date.
     */
    private fun BurnRate.toUi(): BurnRateUi = BurnRateUi(
        averageFormatted = MoneyFormatter.format(series.average, currency),
        projectedFormatted = MoneyFormatter.format(projectedMonthTotal, currency),
        weekTotalFormatted = MoneyFormatter.format(series.total, currency),
        busiestDayFormatted = MoneyFormatter.format(
            series.days.maxOfOrNull { it.total } ?: Money.zero(),
            currency,
        ),
        days = series.days.zip(series.barFractions) { point, fraction ->
            BurnRateDayUi(
                dayOfMonth = point.date.day,
                fraction = fraction,
                isToday = point.date == series.today,
            )
        },
        pace = pace,
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
        /**
         * The week's spend pace and where the month lands at it, or null while no workspace backs a
         * series. Unlike [safeToSpend] this one does not need a budget — a null pace inside it is
         * the "no cap to miss" state, and the chart is drawn either way.
         */
        val burnRate: BurnRateUi? = null,
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

/**
 * The three per-widget sources `combine` cannot take as separate arguments — it tops out at five
 * typed flows and the screen already reads four others.
 */
private data class WidgetSources(
    val layout: DashboardLayoutConfig,
    val safeToSpend: SafeToSpend?,
    val burnRate: BurnRate?,
)

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

/**
 * The burn-rate widget's numbers. Money is formatted here; the sentences around it are built on the
 * screen, which is where the string resources are.
 */
data class BurnRateUi(
    /** Mean spend per day across the charted week. */
    val averageFormatted: String,
    /** Where the month's spend lands if the remaining days each cost the average. */
    val projectedFormatted: String,
    /** What the charted week cost in total — the chart's screen-reader line, not a printed figure. */
    val weekTotalFormatted: String,
    /** The busiest charted day, which is the bar the chart is scaled to. */
    val busiestDayFormatted: String,
    val days: List<BurnRateDayUi>,
    /**
     * The verdict on [projectedFormatted], or null when no monthly budget caps the month. Null is a
     * normal state, not a loading one — see [com.georgeci.moneysurfer.domain.model.BurnRate].
     */
    val pace: BurnRatePace?,
)

/**
 * One bar. [dayOfMonth] rather than a weekday name because the app ships no date locale rules, and
 * a number needs none.
 */
data class BurnRateDayUi(
    val dayOfMonth: Int,
    /** Height against the busiest charted day, 0..1. */
    val fraction: Float,
    /** The last bar — a day still being spent, which the chart draws solid. */
    val isToday: Boolean,
)

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
