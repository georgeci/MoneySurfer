package com.georgeci.moneysurfer.feature.insights

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import kotlin.math.roundToInt

/** [share] is a fraction; this is it as whole percent. */
private const val PERCENT = 100

@optics
sealed interface InsightsState {
    data object Loading : InsightsState

    /**
     * Everything the screen draws for the period the pager is on, already formatted against the
     * workspace base currency — `domain` owns no copy and `MoneyFormatter` needs a currency the
     * composable has no business resolving.
     *
     * [inFlight] covers the one thing the user can wait for here: paging or re-cadencing the period
     * re-runs four `GROUP BY` rollups, and on a large workspace the answer does not arrive in the
     * same frame as the new label. One flag rather than one per control, per AGENTS.md — the pager
     * only ever has one change in the air.
     */
    @optics
    data class Content(
        val mode: DashboardPeriod,
        val period: InsightsPeriodUi,
        /** False once the period contains today; there is nothing to page forward into. */
        val canGoToNextPeriod: Boolean,
        val baseCurrency: String,
        val totalFormatted: String,
        val categories: List<InsightsCategoryUi>,
        val months: List<InsightsMonthUi>,
        val merchants: List<InsightsMerchantUi>,
        /** Currency codes the base-currency filter left out of every figure above, largest first. */
        val hiddenCurrencies: List<String>,
        /** Whether [hiddenCurrencies] is *why* the period looks empty — see `SpendInsights`. */
        val hiddenByBaseCurrency: Boolean,
        val inFlight: Boolean = false,
    ) : InsightsState {

        val isEmpty: Boolean get() = categories.isEmpty()

        companion object
    }

    companion object
}

/**
 * The pager's label reduced to the numbers its copy needs, so the composable resolves month names
 * and week wording from its own resources and the view model stays free of `@Composable`.
 */
sealed interface InsightsPeriodUi {
    /** [monthNumber] is 1-based, matching `Month.number`. */
    data class Month(val monthNumber: Int, val year: Int) : InsightsPeriodUi

    /** ISO week; [weekYear] can differ from the calendar year around New Year. */
    data class Week(val weekNumber: Int, val weekYear: Int) : InsightsPeriodUi
}

/**
 * One row of the category breakdown, and one slice of the donut above it.
 *
 * [name] and [hue] are null for the uncategorized bucket, which has no stored appearance and no
 * name of its own — the screen supplies both, so the bucket keeps its colour when the user switches
 * language.
 */
data class InsightsCategoryUi(
    val id: String,
    val name: String?,
    val hue: Int?,
    val spentFormatted: String,
    val share: Float,
) {
    val sharePercent: Int get() = (share * PERCENT).roundToInt()
}

/** One column of the income-vs-expense chart. The two magnitudes drive the bars, the strings the a11y copy. */
data class InsightsMonthUi(
    /** 1-based, matching `Month.number`. */
    val monthNumber: Int,
    val year: Int,
    val income: Long,
    val expense: Long,
    val incomeFormatted: String,
    val expenseFormatted: String,
)

/** One row of the top-merchants list. [merchant] is never blank — the aggregate excludes those rows. */
data class InsightsMerchantUi(
    val merchant: String,
    val spentFormatted: String,
    val transactionCount: Int,
)

sealed interface InsightsEvent {
    data object OnBackClick : InsightsEvent
    data object OnPreviousPeriodClick : InsightsEvent
    data object OnNextPeriodClick : InsightsEvent
    data class OnPeriodModeChanged(val mode: DashboardPeriod) : InsightsEvent
}

sealed interface InsightsEffect {
    data object NavigateBack : InsightsEffect
}
