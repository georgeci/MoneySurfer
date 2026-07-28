package com.georgeci.moneysurfer.domain.dashboard

/**
 * Registry of the widgets the dashboard can render. The dashboard screen has a renderer for every
 * entry here, so adding a widget is a two-step change: add the constant, then handle it in the
 * screen's `when`. Entries are also the persistence keys of a saved layout — renaming one drops
 * that widget from layouts already stored on device, so prefer adding over renaming. Only the
 * constant name is persisted, so [isPeriodScoped] can be changed freely.
 *
 * @property isPeriodScoped whether this widget reads [DashboardPeriod]. Declared here rather than
 * inferred on the screen so the period switch knows whether it drives anything at all: a layout
 * with every period-scoped widget switched off must not render a control that changes nothing.
 */
enum class DashboardWidgetType(val isPeriodScoped: Boolean = false) {
    Balance,
    QuickActions,
    SafeToSpend(isPeriodScoped = true),
    Accounts,

    /**
     * Spend-oriented, and still **not** period-scoped — a decision, not an oversight.
     *
     * `GenerateInsightsUseCase` builds its own month-to-date window and stands its comparison
     * rules down below seven elapsed days (`MIN_COMPARISON_DAYS`). Under a Week period that
     * counter runs 1..7 and clears the bar only on the last day of the week, so pointing this
     * widget at [DashboardPeriod] would silence it six days in seven. Making the guard a share
     * of the period, or a per-mode minimum, is what has to happen first — see `md/insights.md`
     * Phase 2. Until then the switch does not claim to drive this card.
     */
    Insights,
    Goals,
    RecentTransactions,
}
