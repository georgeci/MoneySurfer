package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodSwitch
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_period_month
import moneysurfer.feature.dashboard.generated.resources.dashboard_period_week
import org.jetbrains.compose.resources.stringResource

/**
 * The one period control the spend-oriented widgets share, rendered above them as screen chrome
 * rather than as a widget of its own: it is not something the customize screen can reorder or
 * switch off, and it belongs to no single card.
 *
 * Right-aligned so it reads as a qualifier on the column below rather than as its heading — the
 * widgets carry their own titles, and a second left-edge element would compete with them.
 */
@Composable
internal fun DashboardPeriodSwitch(
    selected: DashboardPeriod,
    onEvent: (DashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        SurferPeriodSwitch(
            options = DashboardPeriod.entries,
            selected = selected,
            label = { period -> period.label() },
            onSelect = { period -> onEvent(DashboardEvent.OnPeriodChange(period)) },
            modifier = Modifier.testTag(DashboardTestTags.PeriodSwitch),
            optionTestTag = DashboardTestTags::periodOption,
        )
    }
}

@Composable
private fun DashboardPeriod.label(): String = stringResource(
    when (this) {
        DashboardPeriod.Week -> Res.string.dashboard_period_week
        DashboardPeriod.Month -> Res.string.dashboard_period_month
    },
)
