package com.georgeci.moneysurfer.domain.dashboard

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import kotlinx.datetime.LocalDate

/**
 * The span every spend-oriented dashboard widget is read at, chosen once for the whole screen.
 *
 * A closed set of what the dashboard *offers*, not a second period type: [window] delegates to
 * `periodWindow`, so a widget scoped by this and a transactions list scoped by
 * [TransactionPeriodMode] cut the calendar at exactly the same dates. `AllTime` is deliberately
 * unreachable from here — a spend figure with no window has no pace, no days left and nothing to
 * compare against, which is most of what these widgets say.
 *
 * Device-local UI state rather than a stored preference (`md/insights.md` decision 6): it resets
 * to [DEFAULT] on relaunch, the way a filter chip does.
 */
enum class DashboardPeriod {
    Week,
    Month,
    ;

    /** How the same span is spelled for the transactions list and for `periodWindow`. */
    val mode: TransactionPeriodMode
        get() = when (this) {
            Week -> TransactionPeriodMode.Week
            Month -> TransactionPeriodMode.Month
        }

    /**
     * The budget cadence this period reads as. A budget owns its own window, so the selection
     * cannot reshape one — it says which budget a widget should let speak when several are active.
     */
    val budgetPeriod: BudgetPeriod
        get() = when (this) {
            Week -> BudgetPeriod.WEEKLY
            Month -> BudgetPeriod.MONTHLY
        }

    /** The window of this period containing [today]. Always bounded — neither entry is all-time. */
    fun window(today: LocalDate): TransactionPeriodWindow = periodWindow(mode, today)

    companion object {
        /**
         * Month, matching the cadence budgets default to and the one the widgets were specified
         * against — a dashboard that opens on Week would understate every figure on it.
         */
        val DEFAULT = Month
    }
}
