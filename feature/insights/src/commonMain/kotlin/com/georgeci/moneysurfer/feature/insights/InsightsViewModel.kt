package com.georgeci.moneysurfer.feature.insights

import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.CategorySpend
import com.georgeci.moneysurfer.domain.model.InsightsSelection
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendInsights
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.usecase.GetSpendInsightsUseCase
import com.georgeci.moneysurfer.domain.util.isoWeek
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

/** List key for the uncategorized bucket, which has no category id to be keyed by. */
private const val UNCATEGORIZED_ID = "uncategorized"

@KoinViewModel
class InsightsViewModel(
    private val getSpendInsights: GetSpendInsightsUseCase,
    private val clock: ClockUseCase,
) : MviViewModel<InsightsState, InsightsEvent, InsightsEffect>(initialState = InsightsState.Loading) {

    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone).date

    /**
     * The period the screen is pointed at.
     *
     * Deliberately not persisted (`md/insights.md` decision 6): reopening the screen on a month the
     * user last browsed weeks ago would read as missing data, the same call the transactions list
     * made for its own pager.
     */
    private val selection = MutableStateFlow(
        InsightsSelection(mode = DashboardPeriod.DEFAULT, anchor = today),
    )

    init {
        observeInsights()
    }

    override fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.OnBackClick -> postSideEffect(InsightsEffect.NavigateBack)
            InsightsEvent.OnPreviousPeriodClick -> shift(by = -1)
            InsightsEvent.OnNextPeriodClick -> shift(by = 1)
            is InsightsEvent.OnPeriodModeChanged -> select(selection.value.copy(mode = event.mode))
        }
    }

    /**
     * Pages the period, bounded forward by the same rule the arrow is drawn inert on.
     *
     * The bound is repeated here rather than left to the composable because it is a rule about the
     * state, not about the control: a period that has not happened has nothing to show, and its own
     * forward arrow would be inert too — so one stray event lands the screen in a dead period it can
     * only leave by paging back. Backwards is deliberately unbounded; there is no first period.
     */
    private fun shift(by: Int) {
        val content = currentState as? InsightsState.Content
        if (by > 0 && content?.canGoToNextPeriod != true) return
        select(selection.value.shifted(by))
    }

    /**
     * Moves the period and marks the screen busy in the same breath.
     *
     * The flag is raised here rather than in the collector because that is where the wait starts:
     * the new window's rollups are only queried once [selection] emits, and the collector's next
     * emission is what ends it.
     */
    private fun select(next: InsightsSelection) {
        if (next == selection.value) return
        // A no-op while the screen is still Loading — the optic only reaches into Content.
        updateState { InsightsState.content.inFlight.modify(this) { true } }
        selection.value = next
    }

    private fun observeInsights() {
        launch {
            getSpendInsights(selection).collect { insights ->
                updateState { insights?.toContent(today) ?: InsightsState.Loading }
            }
        }
    }
}

/**
 * The screen's state for one answered selection.
 *
 * A null answer maps back to [InsightsState.Loading] rather than to an empty [InsightsState.Content]:
 * null means the workspace or its base currency is not readable yet, and an empty screen would claim
 * the period holds nothing when nothing was actually asked.
 */
private fun SpendInsights.toContent(today: LocalDate): InsightsState.Content {
    // Non-null in practice — neither DashboardPeriod entry is all-time — but the window type also
    // allows an unbounded one, and "no end" is not a period there is a next one of.
    val end = selection.window.to
    return InsightsState.Content(
        mode = selection.mode,
        period = selection.toPeriodUi(),
        canGoToNextPeriod = end != null && end < today,
        baseCurrency = currency.value,
        totalFormatted = MoneyFormatter.format(breakdown.total, currency),
        categories = breakdown.entries.map { it.toUi(currency) },
        months = months.map { it.toUi(currency) },
        merchants = merchants.map { it.toUi(currency) },
        hiddenCurrencies = excludedByCurrency.map { it.currencyCode.value },
        hiddenByBaseCurrency = hiddenByBaseCurrency,
    )
}

private fun InsightsSelection.toPeriodUi(): InsightsPeriodUi = when (mode) {
    DashboardPeriod.Month -> InsightsPeriodUi.Month(monthNumber = anchor.month.number, year = anchor.year)
    DashboardPeriod.Week -> anchor.isoWeek().let { week ->
        InsightsPeriodUi.Week(weekNumber = week.weekNumber, weekYear = week.weekYear)
    }
}

private fun CategorySpend.toUi(currency: CurrencyCode) = InsightsCategoryUi(
    id = category?.id?.value ?: UNCATEGORIZED_ID,
    name = category?.name,
    hue = category?.hue,
    spentFormatted = MoneyFormatter.format(spent, currency),
    share = share,
)

private fun MonthlyNet.toUi(currency: CurrencyCode) = InsightsMonthUi(
    monthNumber = month.month.number,
    year = month.year,
    income = income.minor,
    expense = expense.minor,
    incomeFormatted = MoneyFormatter.format(income, currency),
    expenseFormatted = MoneyFormatter.format(expense, currency),
)

private fun MerchantSpend.toUi(currency: CurrencyCode) = InsightsMerchantUi(
    merchant = merchant,
    spentFormatted = MoneyFormatter.format(total, currency),
    transactionCount = transactionCount,
)
