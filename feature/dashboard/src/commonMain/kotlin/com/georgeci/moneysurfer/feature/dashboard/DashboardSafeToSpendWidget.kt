package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendData
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendEmpty
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_days_left
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_bar_a11y
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_caption
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_over_caption
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_per_day
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_set_budget
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What is still safe to spend this period, and the pace that makes it last.
 *
 * Unlike the quick-actions row this one keeps drawing with nothing behind it: "no budget" is a
 * state the widget is *for*, and the card that says so also carries the link that fixes it.
 */
@Composable
internal fun SafeToSpendWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    SurferSafeToSpendWidget(
        title = stringResource(Res.string.dashboard_safe_to_spend_title),
        data = state.safeToSpend?.toWidgetData(),
        empty = SurferSafeToSpendEmpty(
            title = stringResource(Res.string.dashboard_safe_to_spend_empty_title),
            subtitle = stringResource(Res.string.dashboard_safe_to_spend_empty_subtitle),
            actionLabel = stringResource(Res.string.dashboard_safe_to_spend_set_budget),
            onActionClick = { onEvent(DashboardEvent.OnSetBudgetClick) },
            actionTestTag = DashboardTestTags.SafeToSpendSetBudget,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.SafeToSpend),
    )
}

/**
 * The sentences around the numbers. The bar restates figures the card already prints, so its
 * screen-reader line is the spend against the limit rather than a percentage read off the shape.
 */
@Composable
private fun SafeToSpendUi.toWidgetData(): SurferSafeToSpendData {
    val daysLeftLabel = pluralStringResource(Res.plurals.dashboard_days_left, daysLeft, daysLeft)
    val captionResource = if (isOver) {
        Res.string.dashboard_safe_to_spend_over_caption
    } else {
        Res.string.dashboard_safe_to_spend_caption
    }
    return SurferSafeToSpendData(
        amount = remainingFormatted,
        caption = stringResource(captionResource, limitFormatted, budgetName),
        perDay = stringResource(Res.string.dashboard_safe_to_spend_per_day, perDayFormatted),
        daysLeft = daysLeftLabel,
        progress = progress,
        paceFraction = paceFraction,
        status = status.toWidgetStatus(),
        progressContentDescription = stringResource(
            Res.string.dashboard_safe_to_spend_bar_a11y,
            spentFormatted,
            limitFormatted,
            daysLeftLabel,
        ),
    )
}

/** `uikit` does not depend on `domain`, so the status word crosses over here. */
private fun BudgetStatus.toWidgetStatus(): SurferBudgetStatus = when (this) {
    BudgetStatus.OK -> SurferBudgetStatus.Ok
    BudgetStatus.WARN -> SurferBudgetStatus.Warn
    BudgetStatus.OVER -> SurferBudgetStatus.Over
}
