package com.georgeci.moneysurfer.domain.dashboard

/**
 * The dashboard layout: an ordered list of widgets, top to bottom, in a single vertical column.
 * Order in [items] *is* the render order; there are no rows or columns to reconcile.
 *
 * Stored per device (see `UiPreferences.dashboardLayout`), never synced — a layout is a property
 * of the screen the user is holding, not of the workspace.
 */
data class DashboardLayoutConfig(
    val items: List<DashboardLayoutItem>,
) {

    /** What the dashboard actually renders, in order. */
    val enabledItems: List<DashboardLayoutItem>
        get() = items.filter { it.enabled }

    /**
     * A layout safe to render: at most one entry per widget type, with any type missing from
     * [items] appended in its [DEFAULT] position and style. Persisted layouts written by an older
     * app version know nothing about widgets added since, and dropping those silently would make
     * a new widget invisible until the user resets the dashboard.
     */
    fun normalized(): DashboardLayoutConfig {
        val unique = items.distinctBy { it.type }
        val present = unique.mapTo(mutableSetOf()) { it.type }
        val missing = DEFAULT.items.filterNot { it.type in present }
        return if (unique.size == items.size && missing.isEmpty()) this else DashboardLayoutConfig(unique + missing)
    }

    companion object {
        /**
         * Variant A from the design: the balance headline first, then the accounts strip, goals,
         * and the recent-transactions list. Every widget starts enabled and Hero-sized.
         */
        val DEFAULT = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.Accounts),
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.RecentTransactions),
            ),
        )
    }
}
