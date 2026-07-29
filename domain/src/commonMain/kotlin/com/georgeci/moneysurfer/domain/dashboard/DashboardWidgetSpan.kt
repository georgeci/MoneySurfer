package com.georgeci.moneysurfer.domain.dashboard

/** Columns in the dashboard grid — the lowest common multiple of the halves and thirds below. */
private const val GRID_COLUMNS = 6

/**
 * How much of a dashboard *row* a widget claims once there is a grid to place it in. Only read at
 * expanded width and above; below that the dashboard is one widget per row and every span renders
 * the same.
 *
 * Widths are expressed as columns of a [COLUMNS]-column grid rather than as free fractions, so the
 * useful ones all tile a row exactly — `2+2+2`, `3+3`, `4+2`, `6`. A layout therefore cannot leave a
 * half-column sliver, and rows can be packed greedily without a solver.
 *
 * Declared widest-first: that is the order the picker offers, and [Full] is the default — the width
 * every widget already has in the single-column dashboard.
 */
enum class DashboardWidgetSpan(val columns: Int) {
    Full(GRID_COLUMNS),
    TwoThirds(4),
    Half(3),
    Third(2),
    ;

    companion object {
        /** Columns a dashboard grid row is divided into. */
        const val COLUMNS = GRID_COLUMNS
    }
}
