package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateBar
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateData
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateEmpty
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRatePace
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_average
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_caption
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_chart_a11y
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_off_pace
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_on_track
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_projection
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_title
import org.jetbrains.compose.resources.stringResource

/**
 * What the week has been costing per day, and where the month lands if it keeps costing that.
 *
 * Like the safe-to-spend card this one keeps drawing with no budget behind it — the chart, the
 * average and the projection are arithmetic over transactions. Only the pace pill needs a cap, and
 * it is simply left off without one rather than the widget standing down.
 */
@Composable
internal fun BurnRateWidget(state: DashboardState.Content) {
    SurferBurnRateWidget(
        title = stringResource(Res.string.dashboard_burn_rate_title),
        data = state.burnRate?.toWidgetData(),
        empty = SurferBurnRateEmpty(
            title = stringResource(Res.string.dashboard_burn_rate_empty_title),
            subtitle = stringResource(Res.string.dashboard_burn_rate_empty_subtitle),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.BurnRate),
    )
}

/**
 * The sentences around the numbers. The chart restates money the card prints nowhere else, so its
 * screen-reader line carries the week's total and its busiest day rather than seven bar heights.
 */
@Composable
private fun BurnRateUi.toWidgetData(): SurferBurnRateData = SurferBurnRateData(
    average = stringResource(Res.string.dashboard_burn_rate_average, averageFormatted),
    caption = stringResource(Res.string.dashboard_burn_rate_caption),
    bars = days.map {
        SurferBurnRateBar(
            label = it.dayOfMonth.toString(),
            fraction = it.fraction,
            isToday = it.isToday,
        )
    },
    projection = stringResource(Res.string.dashboard_burn_rate_projection, projectedFormatted),
    pace = pace?.toWidgetPace(),
    chartContentDescription = stringResource(
        Res.string.dashboard_burn_rate_chart_a11y,
        weekTotalFormatted,
        busiestDayFormatted,
    ),
)

/**
 * `uikit` does not depend on `domain`, so the verdict crosses over here.
 *
 * Off pace borrows the Over colour rather than a warning one: the projection has already passed the
 * cap in the only sense a projection can, and a second amber step between "fine" and "not" would be
 * a distinction the maths does not make.
 */
@Composable
private fun BurnRatePace.toWidgetPace(): SurferBurnRatePace = when (this) {
    BurnRatePace.OnTrack -> SurferBurnRatePace(
        label = stringResource(Res.string.dashboard_burn_rate_on_track),
        status = SurferBudgetStatus.Ok,
    )
    BurnRatePace.OffPace -> SurferBurnRatePace(
        label = stringResource(Res.string.dashboard_burn_rate_off_pace),
        status = SurferBudgetStatus.Over,
    )
}
