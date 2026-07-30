package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentMonthWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_delta_down
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_delta_flat
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_delta_up
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_no_budget
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_of_budget
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_month_title
import org.jetbrains.compose.resources.stringResource

/**
 * What this month has cost, how far that is through the budget, and how it compares to last month.
 *
 * Like the burn rate and safe-to-spend cards this one keeps drawing with no budget behind it — the
 * amount is arithmetic over transactions, and "no budget set" is a state the card is partly *for*.
 * Only the bar needs a cap, and it is simply left empty without one rather than filled against a
 * limit nobody set.
 *
 * A dash stands in for the amount only while [DashboardState.Content.spentMonth] itself is null,
 * which is the frame before a workspace and its base currency resolve — never a month that merely
 * booked nothing, which is a real zero and prints as one.
 */
@Composable
internal fun SpentMonthWidget(state: DashboardState.Content) {
    val spentMonth = state.spentMonth
    SurferSpentMonthWidget(
        title = stringResource(Res.string.dashboard_spent_month_title),
        spent = spentMonth?.spentFormatted ?: NO_AMOUNT,
        progress = spentMonth?.progress ?: 0f,
        caption = spentMonth.caption(),
        trailingLabel = spentMonth?.delta?.label(),
        trailingLabelColor = spentMonth?.delta?.color(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .height(spentMonthCardHeight())
            .testTag(DashboardTestTags.SpentMonth),
    )
}

/**
 * The line under the amount: the cap it is measured against, or the fact that nothing caps it.
 *
 * Null while the figure itself is unresolved, which is *not* the same as no budget — a workspace the
 * device has not pulled yet may well have one, and saying otherwise states something about the
 * user's money the app has not read. The card loses nothing by staying quiet: its height is pinned
 * below, and `SurferSpentMonthWidget` drops the caption row entirely when it has nothing for it.
 */
@Composable
private fun SpentMonthUi?.caption(): String? {
    val resolved = this ?: return null
    val cap = resolved.capFormatted ?: return stringResource(Res.string.dashboard_spent_month_no_budget)
    return stringResource(Res.string.dashboard_spent_month_of_budget, cap)
}

/**
 * The delta as a sentence. The direction picks the wording rather than a sign being formatted into
 * the percentage, so a translation can put the arrow wherever its language wants it — and a flat
 * month gets a sentence of its own rather than "+0%".
 */
@Composable
private fun SpentMonthDeltaUi.label(): String = when (trend) {
    SpendTrend.Up -> stringResource(Res.string.dashboard_spent_month_delta_up, percent)
    SpendTrend.Down -> stringResource(Res.string.dashboard_spent_month_delta_down, percent)
    SpendTrend.Flat -> stringResource(Res.string.dashboard_spent_month_delta_flat)
}

/**
 * `uikit` does not depend on `domain`, so the direction is resolved to a colour here.
 *
 * Spending more is the error colour and spending less is the income green — the same reading
 * [SpendTrend.tone] makes in the insights engine, and the pair the rest of the app already uses for
 * money going the wrong and the right way. A flat month takes the widget's default, because there is
 * no direction to colour.
 */
@Composable
private fun SpentMonthDeltaUi.color(): Color? = when (trend) {
    SpendTrend.Up -> AppTheme.materialColors.error
    SpendTrend.Down -> AppTheme.semanticColors.income
    SpendTrend.Flat -> null
}

/**
 * The height this card is drawn at, for the density in scope.
 *
 * Fixed, unlike the list-shaped cards' floor: it draws the same four rows whatever the month holds,
 * so it needs a height rather than a minimum — `SurferSpentMonthWidget` fills what it is given. One
 * value per density, so the size the user picked changes the card's footprint and not only its
 * typography.
 *
 * Internal and shared with the customize picker's tile rather than restated there: the picker exists
 * to show what the two sizes look like, so a tile drawn at a height the dashboard does not use is a
 * thumbnail that misrepresents the choice it is offering.
 */
@Composable
internal fun spentMonthCardHeight(): Dp =
    if (LocalSurferWidgetSize.current == SurferWidgetSize.Compact) {
        SPENT_MONTH_COMPACT_HEIGHT
    } else {
        SPENT_MONTH_HEIGHT
    }

private val SPENT_MONTH_HEIGHT = 160.dp
private val SPENT_MONTH_COMPACT_HEIGHT = 132.dp

/** Stands in for the amount until a workspace and a base currency resolve — see [SpentMonthWidget]. */
private const val NO_AMOUNT = "—"
