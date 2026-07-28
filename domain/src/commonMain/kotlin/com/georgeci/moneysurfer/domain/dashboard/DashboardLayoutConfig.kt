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

    /** The widgets the user switched off — the "Available" half of the customize screen. */
    val disabledItems: List<DashboardLayoutItem>
        get() = items.filterNot { it.enabled }

    /**
     * [type] switched on or off, keeping the item either way so its card style survives the round
     * trip. Switching on appends the widget after the last enabled one — it joins the bottom of the
     * dashboard, rather than reappearing in a middle slot the user cannot see from the toggle.
     * Switching off sends it to the end of [items], so the enabled block stays contiguous.
     *
     * A [type] the layout does not carry is a no-op: only [normalized] adds items.
     */
    fun withWidgetEnabled(type: DashboardWidgetType, enabled: Boolean): DashboardLayoutConfig {
        val target = items.firstOrNull { it.type == type } ?: return this
        if (target.enabled == enabled) return this
        val others = items.filterNot { it.type == type }
        val moved = target.copy(enabled = enabled)
        val stillEnabled = others.filter { it.enabled }
        val stillDisabled = others.filterNot { it.enabled }
        return DashboardLayoutConfig(
            items = if (enabled) {
                stillEnabled + moved + stillDisabled
            } else {
                stillEnabled + stillDisabled + moved
            },
        )
    }

    /**
     * [from] dragged onto the slot [to] currently occupies, within [enabledItems]. Positions are
     * expressed as widget types rather than indices because the customize screen drags rows out of
     * a list that also holds headers and the switched-off section — indices there are not layout
     * positions. Switched-off widgets have no slot to trade, so naming one is a no-op.
     */
    fun withWidgetMoved(from: DashboardWidgetType, to: DashboardWidgetType): DashboardLayoutConfig {
        if (from == to) return this
        val reordered = enabledItems.toMutableList()
        val fromIndex = reordered.indexOfFirst { it.type == from }
        val toIndex = reordered.indexOfFirst { it.type == to }
        if (fromIndex < 0 || toIndex < 0) return this
        reordered.add(toIndex, reordered.removeAt(fromIndex))
        return DashboardLayoutConfig(items = reordered + disabledItems)
    }

    /**
     * [type] restyled. Order and enabled-ness are left alone — the style picker only changes how a
     * widget looks, and it can be opened for a widget that sits anywhere in the list. Like
     * [withWidgetEnabled], naming a [type] the layout does not carry is a no-op.
     */
    fun withCardStyle(type: DashboardWidgetType, cardStyle: DashboardCardStyle): DashboardLayoutConfig {
        val target = items.firstOrNull { it.type == type } ?: return this
        if (target.cardStyle == cardStyle) return this
        return DashboardLayoutConfig(
            items = items.map { if (it.type == type) it.copy(cardStyle = cardStyle) else it },
        )
    }

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
         * Variant A from the design: the balance headline first, the quick actions under it, then
         * safe-to-spend, the accounts strip, the generated insights, goals, and the
         * recent-transactions list. Every widget starts enabled and Hero-sized.
         */
        val DEFAULT = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance),
                DashboardLayoutItem(DashboardWidgetType.QuickActions),
                DashboardLayoutItem(DashboardWidgetType.SafeToSpend),
                DashboardLayoutItem(DashboardWidgetType.Accounts),
                DashboardLayoutItem(DashboardWidgetType.Insights),
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.RecentTransactions),
            ),
        )
    }
}
