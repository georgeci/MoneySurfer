package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.uikit.modifier.SurferContentMaxWidth
import com.georgeci.moneysurfer.uikit.modifier.SurferWideContentMaxWidth
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize

/**
 * Narrowest grid cell that still gets the Expanded widget treatments. A widget's Expanded density is
 * drawn for a phone artboard — 392 dp, of which the widget's own side padding takes 32 — so a cell
 * below that is where Compact starts being the honest choice. See [gridSize].
 */
private val DASHBOARD_EXPANDED_MIN_CELL_WIDTH = 392.dp

/** Row-to-row gap in the grid. Side gutters come from the widgets' own padding, this does not. */
private val DASHBOARD_GRID_ROW_SPACING = 8.dp

/**
 * The dashboard's widgets, laid out for the window they are in: a single scrolling column below
 * expanded width, a [DashboardWidgetSpan.COLUMNS]-column grid at 840 dp and up.
 *
 * Both read the same [DashboardState.Content.layout] in the same order, so switching between them is
 * a matter of how much room each widget gets, never of which widgets are on screen or in what order.
 */
@Composable
internal fun DashboardWidgets(
    state: DashboardState.Content,
    topInset: Dp,
    bottomInset: Dp,
    onEvent: (DashboardEvent) -> Unit,
) {
    val expanded = currentSurferWindowSize() >= SurferWindowSize.Expanded
    // Resolved once, for both layouts: a widget that would draw nothing must not be handed a cell,
    // or the grid row it sits in gets a hole where it stood down. See `rendersWidget`.
    val items = state.layout.enabledItems.filter { state.rendersWidget(it.type) }
    val modifier = Modifier
        .fillMaxSize()
        .surferContentContainer(if (expanded) SurferWideContentMaxWidth else SurferContentMaxWidth)
        .padding(top = topInset)
    if (expanded) {
        DashboardGrid(state, items, bottomInset, onEvent, modifier)
    } else {
        DashboardColumn(state, items, bottomInset, onEvent, modifier)
    }
}

/**
 * The dashboard below expanded width: one widget per row, in layout order, at whatever size each one
 * was configured with. [DashboardLayoutItem.span] is not read here — there is one column, so every
 * span is the whole of it.
 */
@Composable
private fun DashboardColumn(
    state: DashboardState.Content,
    items: List<DashboardLayoutItem>,
    bottomInset: Dp,
    onEvent: (DashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        if (state.periodSwitchVisible) {
            item(key = DashboardTestTags.PeriodSwitch) {
                DashboardPeriodSwitch(selected = state.period, onEvent = onEvent)
            }
        }
        items(
            items = items,
            key = { it.type.name },
        ) { layoutItem ->
            // Per-widget size: the enclosing screen no longer dictates one size for the column,
            // so the Compact branch inside each widget is reachable from the layout config.
            DashboardCell(
                layoutItem = layoutItem,
                size = layoutItem.cardStyle.size,
                state = state,
                onEvent = onEvent,
            )
        }
    }
}

/**
 * The dashboard from expanded width up: the same order, packed into a
 * [DashboardWidgetSpan.COLUMNS]-column grid by each widget's span.
 *
 * No horizontal content padding and no column spacing, because every widget already draws its own
 * 16 dp side padding — the gutters and the outer margin come out of the widgets themselves, exactly
 * as they do in [DashboardColumn]. Only the vertical rhythm needs help: the column gets it from the
 * widgets that pad themselves vertically, which is uneven once cards sit side by side.
 */
@Composable
private fun DashboardGrid(
    state: DashboardState.Content,
    items: List<DashboardLayoutItem>,
    bottomInset: Dp,
    onEvent: (DashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columnWidth = maxWidth / DashboardWidgetSpan.COLUMNS
        LazyVerticalGrid(
            columns = GridCells.Fixed(DashboardWidgetSpan.COLUMNS),
            contentPadding = PaddingValues(bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(DASHBOARD_GRID_ROW_SPACING),
        ) {
            if (state.periodSwitchVisible) {
                item(
                    key = DashboardTestTags.PeriodSwitch,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DashboardPeriodSwitch(selected = state.period, onEvent = onEvent)
                }
            }
            items(
                items = items,
                key = { it.type.name },
                span = { GridItemSpan(it.span.columns) },
            ) { layoutItem ->
                DashboardCell(
                    layoutItem = layoutItem,
                    size = layoutItem.gridSize(columnWidth),
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/** One widget, drawn at [size] rather than at whatever the layout item itself was configured with. */
@Composable
private fun DashboardCell(
    layoutItem: DashboardLayoutItem,
    size: DashboardWidgetSize,
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    CompositionLocalProvider(LocalSurferWidgetSize provides size.toWidgetSize()) {
        DashboardWidget(
            type = layoutItem.type,
            variant = layoutItem.cardStyle.variant,
            state = state,
            onEvent = onEvent,
        )
    }
}

/**
 * The density a widget draws at in a grid cell [DashboardWidgetSpan.columns] columns wide.
 *
 * A narrow span is the third density the dashboard would otherwise need: the Expanded treatments are
 * drawn for a phone's content width, and a third of an 840 dp window is well under that. Rather than
 * add one, a cell too narrow to hold the Expanded card falls back to the Compact one the widget
 * already implements — so the user's size choice still applies wherever there is room for it, and the
 * same layout stays readable at 840 dp and at 1200.
 */
private fun DashboardLayoutItem.gridSize(columnWidth: Dp): DashboardWidgetSize =
    if (columnWidth * span.columns >= DASHBOARD_EXPANDED_MIN_CELL_WIDTH) {
        cardStyle.size
    } else {
        DashboardWidgetSize.Compact
    }

private fun DashboardWidgetSize.toWidgetSize(): SurferWidgetSize = when (this) {
    DashboardWidgetSize.Expanded -> SurferWidgetSize.Expanded
    DashboardWidgetSize.Compact -> SurferWidgetSize.Compact
}
