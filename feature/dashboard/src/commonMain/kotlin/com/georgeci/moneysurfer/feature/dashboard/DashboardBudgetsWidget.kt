package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.uikit.widgets.SurferBudgetItem
import com.georgeci.moneysurfer.uikit.widgets.SurferBudgetsWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_left
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_over
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_spent_of
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_ok
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_over
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_warn
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_title
import org.jetbrains.compose.resources.stringResource

/**
 * The budgets nearest their limit, each as spend against the cap plus the bar between them.
 *
 * Keeps drawing with nothing behind it, the way the safe-to-spend card does: "no budgets" is a
 * state worth a row on the dashboard, and its empty card is what points at the budgets screen.
 */
@Composable
internal fun BudgetsWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    SurferBudgetsWidget(
        items = state.budgets.map { it.toWidgetItem() },
        title = stringResource(Res.string.dashboard_budgets_title),
        seeAllLabel = stringResource(Res.string.dashboard_budgets_see_all),
        onSeeAllClick = { onEvent(DashboardEvent.OnSeeAllBudgetsClick) },
        onItemClick = { item -> onEvent(DashboardEvent.OnBudgetClick(BudgetId(item.id))) },
        emptyTitle = stringResource(Res.string.dashboard_budgets_empty_title),
        emptySubtitle = stringResource(Res.string.dashboard_budgets_empty_subtitle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.Budgets),
        seeAllTestTag = DashboardTestTags.BudgetsSeeAll,
    )
}

/**
 * The sentences around the numbers. The bar restates figures the row already prints, so its
 * screen-reader line is the spend against the limit rather than a percentage read off the shape.
 */
@Composable
private fun BudgetSummaryUi.toWidgetItem(): SurferBudgetItem = SurferBudgetItem(
    id = id.value,
    name = name,
    statusLabel = stringResource(status.labelResource()),
    status = status.toWidgetStatus(),
    spentOfLimit = stringResource(Res.string.dashboard_budgets_spent_of, spentFormatted, limitFormatted),
    // "€25.10 over" rather than a negative remainder: the row states the overshoot, and the sign
    // would read as headroom next to the amounts above it.
    remaining = if (isOver) {
        stringResource(Res.string.dashboard_budgets_over, remainderFormatted)
    } else {
        stringResource(Res.string.dashboard_budgets_left, remainderFormatted)
    },
    progress = progress,
    alertFraction = alertFraction,
)

private fun BudgetStatus.labelResource() = when (this) {
    BudgetStatus.OK -> Res.string.dashboard_budgets_status_ok
    BudgetStatus.WARN -> Res.string.dashboard_budgets_status_warn
    BudgetStatus.OVER -> Res.string.dashboard_budgets_status_over
}
