package com.georgeci.moneysurfer.domain.dashboard

/**
 * One slot of the dashboard layout: which widget, whether the user kept it, how it is styled, and
 * how wide it sits once there is a grid to place it in. A disabled item is retained rather than
 * removed so the widget keeps its position (and its card style) if the user switches it back on.
 *
 * [span] is placement rather than styling, which is why it is a sibling of [cardStyle] and not a
 * field inside it: [cardStyle] says how the widget draws itself, [span] says how much room the
 * dashboard hands it. At compact width there is only one column, so [span] is not read at all.
 */
data class DashboardLayoutItem(
    val type: DashboardWidgetType,
    val enabled: Boolean = true,
    val cardStyle: DashboardCardStyle = DashboardCardStyle.EXPANDED,
    val span: DashboardWidgetSpan = DashboardWidgetSpan.Full,
)
