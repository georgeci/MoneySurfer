package com.georgeci.moneysurfer.feature.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodArrow
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodPager
import moneysurfer.feature.insights.generated.resources.Res
import moneysurfer.feature.insights.generated.resources.insights_months
import moneysurfer.feature.insights.generated.resources.insights_period_mode_month
import moneysurfer.feature.insights.generated.resources.insights_period_mode_week
import moneysurfer.feature.insights.generated.resources.insights_period_next
import moneysurfer.feature.insights.generated.resources.insights_period_previous
import moneysurfer.feature.insights.generated.resources.insights_period_week
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * The period control, shared in substance with the dashboard's (#296): the same two cadences, the
 * same `periodWindow` behind them, plus the arrows the dashboard has no use for.
 *
 * One control rather than a switch beside a pager, following the transactions list: the pill is the
 * only place on the screen where the period is already the subject, so the cadence menu hangs off
 * its label instead of competing with it for the header.
 *
 * The arrows stay visible when there is nothing to page into and go inert instead — hiding the
 * forward arrow at the current period would make the control jump width every time the user pages
 * back.
 */
@Composable
internal fun InsightsPeriodPager(
    state: InsightsState.Content,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SurferPeriodPager(
            label = state.period.label(),
            sublabel = state.period.sublabel(),
            previous = SurferPeriodArrow(
                onClick = { onEvent(InsightsEvent.OnPreviousPeriodClick) },
                contentDescription = stringResource(Res.string.insights_period_previous),
            ),
            next = SurferPeriodArrow(
                onClick = { onEvent(InsightsEvent.OnNextPeriodClick) },
                enabled = state.canGoToNextPeriod,
                contentDescription = stringResource(Res.string.insights_period_next),
            ),
            onLabelClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DashboardPeriod.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        menuExpanded = false
                        onEvent(InsightsEvent.OnPeriodModeChanged(mode))
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardPeriod.label(): String = stringResource(
    when (this) {
        DashboardPeriod.Month -> Res.string.insights_period_mode_month
        DashboardPeriod.Week -> Res.string.insights_period_mode_week
    },
)

@Composable
private fun InsightsPeriodUi.label(): String = when (this) {
    is InsightsPeriodUi.Month -> stringArrayResource(Res.array.insights_months)[monthNumber - 1]
    is InsightsPeriodUi.Week -> stringResource(Res.string.insights_period_week, weekNumber)
}

/** The year the label leaves out — the calendar year for a month, the week-based one for a week. */
@Composable
private fun InsightsPeriodUi.sublabel(): String = when (this) {
    is InsightsPeriodUi.Month -> year.toString()
    is InsightsPeriodUi.Week -> weekYear.toString()
}
