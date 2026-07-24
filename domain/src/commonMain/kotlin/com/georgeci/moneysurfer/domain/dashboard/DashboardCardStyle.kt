package com.georgeci.moneysurfer.domain.dashboard

/**
 * How much room a widget takes in the column. Maps 1:1 onto `uikit.widgets.SurferWidgetSize`;
 * declared in the domain so a layout can be persisted without leaking UI types into `data`.
 */
enum class DashboardWidgetSize {
    Hero,
    Compact,
}

/**
 * Visual treatment of a single dashboard widget: its [size] plus an optional widget-specific
 * [variant] key (e.g. a chart flavour) that only the widget that defines it understands. The
 * variant is free-form on purpose — each widget task can introduce its own values without
 * touching this type or the persistence format.
 */
data class DashboardCardStyle(
    val size: DashboardWidgetSize = DashboardWidgetSize.Hero,
    val variant: String? = null,
) {
    companion object {
        val HERO = DashboardCardStyle(DashboardWidgetSize.Hero)
        val COMPACT = DashboardCardStyle(DashboardWidgetSize.Compact)
    }
}
