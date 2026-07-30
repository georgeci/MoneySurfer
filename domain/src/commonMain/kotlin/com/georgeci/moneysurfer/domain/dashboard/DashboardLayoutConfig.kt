package com.georgeci.moneysurfer.domain.dashboard

/**
 * The dashboard layout: an ordered list of widgets. Order in [items] *is* the render order.
 *
 * At compact width that order is the whole story — one widget per row, top to bottom. From expanded
 * width up the same order is packed into a [DashboardWidgetSpan.COLUMNS]-column grid, greedily: a
 * widget joins the current row if its [DashboardLayoutItem.span] still fits, and starts a new one if
 * it does not. There are still no rows to reconcile — a row is whatever the order and the spans add
 * up to, so reordering or resizing one widget can never orphan another.
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
     * Whether anything on screen reads [DashboardPeriod]. The period switch is chrome for the
     * widgets under it, so a layout that shows none of them shows no switch either — a Week/Month
     * control that visibly changes nothing reads as a broken one.
     */
    val hasPeriodScopedWidget: Boolean
        get() = enabledItems.any { it.type.isPeriodScoped }

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
     * [from] dragged onto the slot [to] currently occupies. Positions are expressed as widget types
     * rather than indices because the customize screen drags rows out of a list that also holds
     * headers and the switched-off section — indices there are not layout positions.
     *
     * The two sections are one draggable list: dropping a widget onto a slot in the *other* section
     * moves it there, which is the same edit the +/− button makes. So a drop adopts the target's
     * [DashboardLayoutItem.enabled], and the enabled block stays contiguous either way — the row
     * lands where the finger left it, on the side of the boundary it was dropped.
     */
    fun withWidgetMoved(from: DashboardWidgetType, to: DashboardWidgetType): DashboardLayoutConfig {
        if (from == to) return this
        // Rendered order, not storage order: enabled first, switched-off under them.
        val ordered = (enabledItems + disabledItems).toMutableList()
        val fromIndex = ordered.indexOfFirst { it.type == from }
        val toIndex = ordered.indexOfFirst { it.type == to }
        if (fromIndex < 0 || toIndex < 0) return this
        val landsEnabled = ordered[toIndex].enabled
        ordered.add(toIndex, ordered.removeAt(fromIndex).copy(enabled = landsEnabled))
        return DashboardLayoutConfig(items = ordered)
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
     * [type] rewidened or narrowed for the grid. Like [withCardStyle] this touches nothing else:
     * a span is read only from expanded width up, and changing one widget's width there must not
     * shuffle the order the compact dashboard renders. Naming a [type] the layout does not carry is
     * a no-op.
     */
    fun withSpan(type: DashboardWidgetType, span: DashboardWidgetSpan): DashboardLayoutConfig {
        val target = items.firstOrNull { it.type == type } ?: return this
        if (target.span == span) return this
        return DashboardLayoutConfig(
            items = items.map { if (it.type == type) it.copy(span = span) else it },
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
         * safe-to-spend, the burn rate that explains it, the budgets both are read off, where
         * the period's money went, the accounts strip, the same spend as a donut, the generated
         * insights, goals, and the recent-transactions list. Every widget starts enabled and
         * Hero-sized.
         *
         * The spans are that same order read as the desktop mock's three bands: a hero balance
         * beside the shortcuts, a row of three stat cards, the spend chart with the accounts rail
         * next to it, insights and goals paired, and the activity list full width at the bottom.
         * Each row sums to [DashboardWidgetSpan.COLUMNS] exactly, so the default grid has no gaps.
         *
         * The donut takes a band of its own at [DashboardWidgetSpan.Full] — a card that is already
         * a wide row (chart beside legend) reads fine at that width, and it is the only span that
         * seats a new widget without renarrowing the cards around it, which would have changed the
         * default dashboard of every user who never opened the customize screen.
         */
        val DEFAULT = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Balance, span = DashboardWidgetSpan.TwoThirds),
                DashboardLayoutItem(DashboardWidgetType.QuickActions, span = DashboardWidgetSpan.Third),
                DashboardLayoutItem(DashboardWidgetType.SafeToSpend, span = DashboardWidgetSpan.Third),
                DashboardLayoutItem(DashboardWidgetType.BurnRate, span = DashboardWidgetSpan.Third),
                DashboardLayoutItem(DashboardWidgetType.Budgets, span = DashboardWidgetSpan.Third),
                DashboardLayoutItem(DashboardWidgetType.SpentByCategory, span = DashboardWidgetSpan.TwoThirds),
                DashboardLayoutItem(DashboardWidgetType.Accounts, span = DashboardWidgetSpan.Third),
                DashboardLayoutItem(DashboardWidgetType.CategoriesDonut, span = DashboardWidgetSpan.Full),
                DashboardLayoutItem(DashboardWidgetType.Insights, span = DashboardWidgetSpan.Half),
                DashboardLayoutItem(DashboardWidgetType.Goals, span = DashboardWidgetSpan.Half),
                DashboardLayoutItem(DashboardWidgetType.RecentTransactions),
            ),
        )
    }
}
