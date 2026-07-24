package com.georgeci.moneysurfer.domain.dashboard

/**
 * One row of the dashboard layout: which widget, whether the user kept it, and how it is styled.
 * A disabled item is retained rather than removed so the widget keeps its position (and its card
 * style) if the user switches it back on.
 */
data class DashboardLayoutItem(
    val type: DashboardWidgetType,
    val enabled: Boolean = true,
    val cardStyle: DashboardCardStyle = DashboardCardStyle.HERO,
)
