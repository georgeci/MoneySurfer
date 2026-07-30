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
import com.georgeci.moneysurfer.domain.model.BudgetProgress
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.BurnRate
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.model.CategorySpend
import com.georgeci.moneysurfer.domain.model.ConvertedTotal
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.model.MonthlyNetHistory
import com.georgeci.moneysurfer.domain.model.SafeToSpend
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.model.SpentByCategory
import com.georgeci.moneysurfer.domain.model.SpentMonth
import com.georgeci.moneysurfer.domain.model.TransactionSplitGroup
import com.georgeci.moneysurfer.domain.model.buildBalanceTrend
import com.georgeci.moneysurfer.domain.model.safeToSpend
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetActiveBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBurnRateUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetMonthlyNetHistoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSafeToSpendUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSpentMonthUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * One reader per widget: the dashboard is the one screen that composes every other feature's
 * numbers, so its dependency list grows by one each time a widget is wired. Grouping them would
 * only move the list somewhere else — see `observeDashboard`, where the flows themselves *are*
 * folded, because there `combine`'s five-flow ceiling forces it.
 */
@KoinViewModel
@Suppress("LongParameterList")
class DashboardViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val getRecentTransactions: GetRecentTransactionsUseCase,
    private val getGoals: GetGoalsUseCase,
    private val getExchangeRates: GetExchangeRatesUseCase,
    private val getSafeToSpend: GetSafeToSpendUseCase,
    private val getCategorySpend: GetCategorySpendUseCase,
    private val getActiveBudgetProgress: GetActiveBudgetProgressUseCase,
    private val getBurnRate: GetBurnRateUseCase,
    private val getSpentMonth: GetSpentMonthUseCase,
    private val generateInsights: GenerateInsightsUseCase,
    private val convertAccountsTotal: ConvertAccountsTotalUseCase,
    private val getMonthlyNetHistory: GetMonthlyNetHistoryUseCase,
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
        // The chrome and the four widget readers are folded into one source because `combine`
        // tops out at five typed flows, and these are never read apart.
        //
        // The period reaches safe-to-spend as a flow, not as this combine's value: `getSafeToSpend`
        // re-picks the budget in place that way, instead of restarting the workspace-wide
        // transaction query on every tap of the switch. The cost is one intermediate emission on a
        // change — the new period beside the previous budget's figures — which resolves on the next
        // frame and only differs at all when the workspace runs budgets on both cadences.
        //
        // The sixth source arrived with the budgets card, so the two the screen draws *around* the
        // widgets — which layout, which span — ride together in [DashboardChrome] and free a slot.
        // They are the pair to fold: both are local UI state that is always present, so neither can
        // be the one a widget is waiting on.
        val chrome = combine(layout, period, ::DashboardChrome)
        val widgetSources = combine(
            chrome,
            getSafeToSpend(period.map { it.budgetPeriod }).onStart { emit(null) },
            // The budgets card lists what safe-to-spend picks one of. It reads the progress list
            // itself rather than the headline, and takes no period: a budget owns its own window,
            // so the switch cannot reshape which caps the user is tracking.
            getActiveBudgetProgress().onStart { emit(emptyList()) },
            // Burn rate takes no period: it projects to month-end off a fixed seven-day chart.
            // See `DashboardWidgetType.BurnRate` for why that is a scoping call, not an omission.
            getBurnRate().onStart { emit(null) },
            getCategorySpend(period).onStart { emit(null) },
            ::WidgetSources,
        )
        // The balance widget's two non-account inputs, folded for the same reason the chrome is: the
        // cached FX table is what turns the account list into one figure, and the monthly nets are
        // what shape the curve under it. Neither is read anywhere else, and together they leave the
        // fifth `combine` slot below free.
        val balanceSources = combine(
            getExchangeRates().onStart { emit(null) },
            getMonthlyNetHistory().onStart { emit(null) },
            ::BalanceSources,
        )
        launch {
            combine(
                getAccounts().onStart { emit(emptyList()) },
                getRecentTransactions(),
                getGoals(),
                balanceSources,
                widgetSources,
                // Read by name rather than destructured: at five members the positional form is
                // one reordered field away from a silent swap, and detekt caps it at three.
            ) { accounts, transactions, goals, balanceInputs, widgets ->
                val balance = accounts.convertedTotal(balanceInputs.rates)
                    ?.toBalanceUi(balanceInputs.netHistory)
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
                    formattedTrendDelta = balance?.trend?.text,
                    isTrendDeltaNegative = balance?.trend?.isNegative == true,
                    balanceSeries = balance?.trend?.series.orEmpty(),
                    goals = goals.take(DASHBOARD_GOALS_LIMIT).map { it.toUi() },
                    safeToSpend = widgets.safeToSpend?.toUi(),
                    spentByCategory = widgets.categorySpend?.toUi().orEmpty(),
                    spentByCategoryTotal = widgets.categorySpend
                        ?.let { MoneyFormatter.format(it.total, it.currency) },
                    budgets = widgets.budgetProgress.toBudgetsUi(),
                    burnRate = widgets.burnRate?.toUi(),
                    isOffline = isOffline,
                    transferEnabled = transferEnabled,
                    layout = widgets.chrome.layout,
                    period = widgets.chrome.period,
                )
            }
                // Joined here rather than folded into a pair above: the insights are the slowest
                // input — two aggregates plus the category tree — so keeping them last lets the
                // rest of the dashboard draw without waiting on them.
                .combine(generateInsights().onStart { emit(emptyList()) }) { content, insights ->
                    content.copy(insights = insights.map { it.toUi() })
                }
                // Appended for the same reason the insights are, not because it is slow: the fold
                // above is already at `combine`'s five-flow ceiling, and chaining one reader on is
                // the cheaper edit than repacking six widget sources into a second holder type.
                // Takes no period — see `DashboardWidgetType.SpentMonth`.
                .combine(getSpentMonth().onStart { emit(null) }) { content, spentMonth ->
                    content.copy(spentMonth = spentMonth?.toUi())
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
     *
     * That fallback headline gets no trend: [history] is quoted in the base currency, and hanging
     * its curve off a total that is one currency's bucket would draw a shape belonging to neither.
     * Neither does a headline that took a rate to assemble — see [coversWholeHeadline].
     */
    private fun ConvertedTotal.toBalanceUi(history: MonthlyNetHistory?): BalanceUi {
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
                trend = history?.takeIf { coversWholeHeadline }?.toTrendUi(converted, baseCurrency),
            )
        }
    }

    /**
     * Whether base-currency history can account for the whole headline, which is what it takes for a
     * delta folded out of that history to be the movement of *this* figure.
     *
     * The aggregate behind the nets filters on the base currency and the app caches only today's
     * rates, so a foreign account's movement is outside the fold no matter how it is anchored. It is
     * inside the headline, though, the moment a rate could price it — and "+$0.00 this month" beside
     * a balance that grew by a third of itself is the class of answer
     * [ConvertedTotal] exists to prevent. So the trend stands down unless every balance in the
     * workspace was already in the base currency: nothing left unconverted ([ConvertedTotal.isComplete])
     * and no quote needed to build the total (a null `asOf`, which is exactly that signal).
     *
     * Costs multi-currency workspaces their trend until the day transactions carry a rate snapshot.
     * A figure that silently omits money is worse than no figure.
     */
    private val ConvertedTotal.coversWholeHeadline: Boolean
        get() = isComplete && asOf == null

    /**
     * This month's movement and the curve behind it, or null when there is nothing honest to draw:
     *
     * - the nets are *filtered* to their own workspace's base currency, and the total is converted
     *   into whatever the cached FX table is based on. The two agree except in the window between a
     *   base-currency change and the next rate refresh, and in that window a fold would subtract
     *   euros from dollars;
     * - a window that booked nothing has no curve. The fold would still produce six points — the
     *   present balance repeated — and a flat line across half a year is a claim about months the
     *   aggregate cannot see, since opening balances are outside its monthly nets by construction.
     */
    private fun MonthlyNetHistory.toTrendUi(total: Money, base: CurrencyCode): BalanceTrendUi? {
        if (currency != base || nets.isEmpty()) return null
        val trend = buildBalanceTrend(currentBalance = total, nets = nets, months = months)
        val formatted = MoneyFormatter.format(trend.delta, base)
        return BalanceTrendUi(
            // Locale formatting only signs negatives; a flat month reads as "+" too, since "no
            // change" is not a loss. Same call as the account details chart's delta.
            text = if (trend.delta.isNegative()) formatted else "+$formatted",
            isNegative = trend.delta.isNegative(),
            // Major units: the curve only needs its own shape, and minor units are a Long the
            // chart would have to narrow anyway.
            series = trend.points.map { (it.balance.minor / Money.MINOR_PER_MAJOR).toFloat() },
        )
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
            // Lazily, so a workspace with thirty budgets formats money for the three rows the card
            // draws rather than for all thirty on every transaction change.
            .asSequence()
            .mapNotNull { it.toUiOrNull() }
            .take(DASHBOARD_BUDGETS_LIMIT)
            .toList()

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
        /**
         * What the balance moved by this month, signed and formatted, or null when no window of
         * base-currency history backs a figure — see `toTrendUi`.
         */
        val formattedTrendDelta: String?,
        /** Whether [formattedTrendDelta] went down. Picks the arrow the widget draws beside it. */
        val isTrendDeltaNegative: Boolean = false,
        /**
         * The balance at the close of each of the charted months, oldest first, in major units.
         * Empty when there is no curve to draw — the widget needs two points to draw one.
         */
        val balanceSeries: List<Float> = emptyList(),
        val goals: List<GoalUi> = emptyList(),
        /** What is still safe to spend this period, or null while no active budget backs a number. */
        val safeToSpend: SafeToSpendUi? = null,
        /** The active budgets worth a row, most pressing first — empty when none are tracked. */
        val budgets: List<BudgetSummaryUi> = emptyList(),
        /**
         * Where the selected period's money went, largest spender first — empty while it holds no
         * spend, which is the spent-by-category widget's own empty state rather than a missing one.
         */
        val spentByCategory: List<CategorySpendUi> = emptyList(),
        /**
         * Everything [spentByCategory] adds up to, formatted — the donut's centre figure. Kept
         * beside the rows rather than re-summed on the screen: the rows carry formatted strings,
         * not money, so a screen-side sum would have to parse its way back to the amounts. Null
         * while no workspace backs a breakdown, which is not the same as a breakdown of zero.
         */
        val spentByCategoryTotal: String? = null,
        /**
         * The week's spend pace and where the month lands at it, or null while no workspace backs a
         * series. Unlike [safeToSpend] this one does not need a budget — a null pace inside it is
         * the "no cap to miss" state, and the chart is drawn either way.
         */
        val burnRate: BurnRateUi? = null,
        /**
         * What this month has cost, or null while no workspace and base currency back the figure.
         * Like [burnRate] this one does not need a budget — a null cap inside it is the "no budget
         * set" state the card is partly *for*, and the amount is drawn either way.
         */
        val spentMonth: SpentMonthUi? = null,
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

/**
 * The per-widget sources `combine` cannot take as separate arguments — it tops out at five typed
 * flows and the screen already reads four others.
 */
private data class WidgetSources(
    val chrome: DashboardChrome,
    val safeToSpend: SafeToSpend?,
    /** Every active budget's progress — the rows the budgets card draws. */
    val budgetProgress: List<BudgetProgress>,
    val burnRate: BurnRate?,
    val categorySpend: SpentByCategory?,
)

/**
 * The month's spend as the card draws it. A file-level mapper like [SpentByCategory.toUi] below —
 * it reads nothing off the view model, and the class is at detekt's function ceiling.
 *
 * [SpentMonthUi.capFormatted] carries the "is there a budget" answer rather than a second boolean:
 * the caption is either "of <cap>" or the no-budget line, and one nullable string cannot disagree
 * with itself. The bar reads 0 without a cap — there is no limit to fill it against, and inventing
 * one from the spend would draw a full bar on an unbudgeted month.
 */
private fun SpentMonth.toUi(): SpentMonthUi = SpentMonthUi(
    spentFormatted = MoneyFormatter.format(spent, currency),
    capFormatted = cap?.let { MoneyFormatter.format(it, currency) },
    progress = capFraction ?: 0f,
    delta = delta?.let { SpentMonthDeltaUi(trend = it.trend, percent = it.changePercent) },
)

/**
 * The breakdown's rows, largest spender first. Money is formatted here against the breakdown's
 * own currency — non-null by construction, so nothing has to invent one.
 */
private fun SpentByCategory.toUi(): List<CategorySpendUi> = entries.map { it.toUi(currency) }

private fun CategorySpend.toUi(currency: CurrencyCode) = CategorySpendUi(
    categoryId = category?.id?.value,
    name = category?.name,
    hue = category?.hue,
    spentFormatted = MoneyFormatter.format(spent, currency),
    share = share,
    cap = cap?.let {
        CategoryCapUi(
            limitFormatted = MoneyFormatter.format(it.limit, currency),
            status = it.status,
            // Non-null whenever `cap` is — the two come off the same entry.
            progress = capFraction ?: 0f,
        )
    },
)

/**
 * What the screen draws around the widgets: which cards, in what order, and the span they are all
 * read at. Folded into one flow so the five `combine` takes can cover six sources.
 */
private data class DashboardChrome(
    val layout: DashboardLayoutConfig,
    val period: DashboardPeriod,
)

/** Everything the balance widget draws, already formatted. */
private data class BalanceUi(
    val headline: String?,
    val notConverted: List<String>,
    val asOf: String?,
    /** Null when no base-currency history could be anchored on the headline. */
    val trend: BalanceTrendUi? = null,
)

/** The balance card's movement half: the delta as a signed string, and the curve behind it. */
private data class BalanceTrendUi(
    val text: String,
    val isNegative: Boolean,
    val series: List<Float>,
)

/**
 * The two flows behind the headline figure and its curve, folded into one so the screen's
 * five-source `combine` still fits. See `observeDashboard`.
 */
private data class BalanceSources(
    val rates: ExchangeRateSnapshot?,
    val netHistory: MonthlyNetHistory?,
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
 * One row of the spent-by-category widget. The label for the uncategorized bucket and the sentences
 * around the numbers are built on the screen, which is where the string resources are.
 */
data class CategorySpendUi(
    /** Null for the uncategorized bucket, which is a real slice with no category behind it. */
    val categoryId: String?,
    /** Null for the uncategorized bucket — the screen owns that label. */
    val name: String?,
    /** The category's stored hue, or null for the bucket that has no stored appearance. */
    val hue: Int?,
    val spentFormatted: String,
    /** Share of the selected period's total spend, `0f..1f`. */
    val share: Float,
    /** The cap on this category, or null while nothing caps it. */
    val cap: CategoryCapUi? = null,
) {

    /** [share] as whole percent, for the caption of a row that has no cap to state instead. */
    val sharePercent: Int get() = (share * PERCENT).roundToInt()
}

/** The cap a single-category budget puts on one row, already formatted. */
data class CategoryCapUi(
    val limitFormatted: String,
    val status: BudgetStatus,
    /** Spend against the cap; can exceed 1, which the meter caps rather than overdrawing. */
    val progress: Float,
)

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
 * The spent-this-month card's numbers. Money is formatted here; the sentences around it are built on
 * the screen, which is where the string resources are.
 */
data class SpentMonthUi(
    val spentFormatted: String,
    /** The monthly cap, or null when no budget sets one — which is the caption the card shows. */
    val capFormatted: String?,
    /** Spend against the cap, `0f` without one. Can exceed 1, which the bar clamps. */
    val progress: Float,
    /** How the month compares to the last one, or null when no honest comparison is available. */
    val delta: SpentMonthDeltaUi? = null,
)

/**
 * The month-over-month change. [percent] is a magnitude — [trend] picks which sentence and which
 * colour it is printed in, so a sign is never formatted into the number itself.
 */
data class SpentMonthDeltaUi(
    val trend: SpendTrend,
    val percent: Int,
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

/**
 * The navigation half of the dashboard's contract as a table. A file-level function rather than a
 * method on the view model for the same reason `DashboardNavigation.navigate` is one (issue #404):
 * it is a dozen one-line branches where swapping two lines still compiles, still passes every other
 * test, and lands the user on the wrong screen — so it has to be drivable by a plain spec with no
 * view model to build. Between the two tables, event → effect → destination is covered end to end.
 *
 * Split out of `onEvent` because the dashboard is a hub: all but one of its events do nothing but
 * name a destination, and leaving them inline left the one event that *changes this screen* as the
 * last line of a list that reads as boilerplate.
 */
// The table's "complexity" is the number of places the dashboard can reach, not tangled logic —
// every branch is one destination. Same call as `SettingsScreen` and `DashboardNavigation.navigate`.
@Suppress("CyclomaticComplexMethod")
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
    DashboardEvent.OnSeeAllBudgetsClick -> DashboardEffect.NavigateToBudgets
    is DashboardEvent.OnBudgetClick -> DashboardEffect.NavigateToBudgetDetails(budgetId)
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
    data object OnSeeAllBudgetsClick : Navigate
    data class OnBudgetClick(val budgetId: BudgetId) : Navigate

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
    data object NavigateToBudgets : DashboardEffect
    data class NavigateToBudgetDetails(val budgetId: BudgetId) : DashboardEffect
}

private const val RECENT_TRANSACTIONS_LIMIT = 5

/** The widget shows two rows at most; reading more of the list would be wasted work. */
private const val DASHBOARD_GOALS_LIMIT = 2

/** Three rows is what the full-size budgets card draws; the compact one keeps the first. */
private const val DASHBOARD_BUDGETS_LIMIT = 3

/**
 * Alert thresholds are stored as whole percents and the bar wants a fraction; a share is a fraction
 * and its caption wants a whole percent. One constant serves both directions.
 */
private const val PERCENT = 100f
