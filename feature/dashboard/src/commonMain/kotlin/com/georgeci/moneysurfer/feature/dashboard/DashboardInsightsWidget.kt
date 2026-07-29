package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.insight.InsightTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightItem
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightTone
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsWidget
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_category_down_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_category_up_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_comparison_body
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_empty_text
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_period_down_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_period_flat_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_period_up_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_subscriptions_body
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_subscriptions_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_uncategorized
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The generated insights, and the copy that turns each rule's finding into a sentence.
 *
 * Lives beside `DashboardScreen` rather than in it: six sentence shapes is more copy than any other
 * widget on the dashboard needs, and the screen's job is to route a widget type to one call.
 *
 * Draws its own empty state rather than standing down the way the quick-actions row does —
 * "nothing notable this period" is an answer, and a widget the user switched on should not vanish
 * because the engine had a quiet month.
 */
@Composable
internal fun InsightsWidget(state: DashboardState.Content, variant: String?) {
    SurferInsightsWidget(
        items = state.insights.map { it.toWidgetItem() },
        title = stringResource(Res.string.dashboard_insights_title),
        variant = SurferInsightsVariant.fromKey(variant),
        emptyText = stringResource(Res.string.dashboard_insights_empty_text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // Only the empty state needs a floor to centre itself in; cards size themselves, and
            // a compact carousel showing one of them would otherwise hang dead space underneath.
            .defaultMinSize(minHeight = if (state.insights.isEmpty()) DASHBOARD_WIDGET_MIN_HEIGHT else Dp.Unspecified)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.Insights),
    )
}

/**
 * The card an insight draws. The rule that produced it already chose the tone in `domain`; this
 * side picks the words and the icon that go with it.
 */
@Composable
private fun InsightUi.toWidgetItem(): SurferInsightItem = SurferInsightItem(
    id = id,
    tone = when (tone) {
        InsightTone.Good -> SurferInsightTone.Good
        InsightTone.Warn -> SurferInsightTone.Warn
        InsightTone.Neutral -> SurferInsightTone.Neutral
    },
    icon = when (kind) {
        InsightKind.CategoryUp, InsightKind.PeriodUp -> SurferIcons.ArrowUp
        InsightKind.CategoryDown, InsightKind.PeriodDown -> SurferIcons.ArrowDown
        InsightKind.PeriodFlat -> SurferIcons.Calendar
        InsightKind.Subscriptions -> SurferIcons.Sync
    },
    title = insightTitle(),
    body = insightBody(),
)

@Composable
private fun InsightUi.insightTitle(): String {
    // A slice with no category is a real slice, so it gets a name rather than being skipped.
    val subject = label ?: stringResource(Res.string.dashboard_insights_uncategorized)
    return when (kind) {
        InsightKind.CategoryUp ->
            stringResource(Res.string.dashboard_insights_category_up_title, subject, percent)
        InsightKind.CategoryDown ->
            stringResource(Res.string.dashboard_insights_category_down_title, subject, percent)
        InsightKind.PeriodUp -> stringResource(Res.string.dashboard_insights_period_up_title, percent)
        InsightKind.PeriodDown -> stringResource(Res.string.dashboard_insights_period_down_title, percent)
        InsightKind.PeriodFlat -> stringResource(Res.string.dashboard_insights_period_flat_title)
        InsightKind.Subscriptions ->
            pluralStringResource(Res.plurals.dashboard_insights_subscriptions_title, count, count)
    }
}

@Composable
private fun InsightUi.insightBody(): String = when (kind) {
    InsightKind.CategoryUp,
    InsightKind.CategoryDown,
    InsightKind.PeriodUp,
    InsightKind.PeriodDown,
    InsightKind.PeriodFlat,
    -> stringResource(Res.string.dashboard_insights_comparison_body, amount, comparison)
    InsightKind.Subscriptions ->
        stringResource(Res.string.dashboard_insights_subscriptions_body, amount)
}
