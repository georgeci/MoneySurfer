package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.widgets.SurferRecurringItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecurringWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_due_today
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_due_tomorrow
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_recurring_title
import org.jetbrains.compose.resources.stringResource

/**
 * The scheduled payments about to land: the soonest few recurring rules, each with the day it falls
 * due and what it will cost.
 *
 * The rows are handed over exactly as [DashboardState.Content.upcoming] holds them — already sorted
 * and already capped — and how many of them fit is the widget's own call (Hero 3 / Compact 2). The
 * action opens the transactions list rather than a rules screen, which the app does not have yet.
 */
@Composable
internal fun RecurringWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    SurferRecurringWidget(
        items = state.upcoming.map { it.toWidgetItem() },
        title = stringResource(Res.string.dashboard_recurring_title),
        actionLabel = stringResource(Res.string.dashboard_recurring_see_all),
        onActionClick = { onEvent(DashboardEvent.OnSeeAllTransactionsClick) },
        emptyTitle = stringResource(Res.string.dashboard_recurring_empty_title),
        emptySubtitle = stringResource(Res.string.dashboard_recurring_empty_subtitle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.Recurring),
    )
}

/**
 * The due label: "Today" and "Tomorrow" for the two days a user plans around by name, and the ISO
 * date for everything further out — the app ships no date locale rules, and a date is unambiguous
 * without them. Same call as the balance card's "as of" line.
 */
@Composable
private fun UpcomingRecurringUi.toWidgetItem(): SurferRecurringItem = SurferRecurringItem(
    id = id.value,
    name = name,
    dueLabel = when (daysUntil) {
        0 -> stringResource(Res.string.dashboard_recurring_due_today)
        1 -> stringResource(Res.string.dashboard_recurring_due_tomorrow)
        else -> dueDateIso
    },
    amountFormatted = amountFormatted,
    isImminent = isImminent,
)
