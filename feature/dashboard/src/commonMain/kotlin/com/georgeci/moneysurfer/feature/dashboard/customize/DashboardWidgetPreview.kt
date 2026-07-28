package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferScaleToWidth
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferAddAccountCta
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceFootnote
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalItem
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferQuickActionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_manage
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_transaction
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_cash
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_everyday
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_savings
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_goal_laptop
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_goal_trip
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_coffee
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_groceries
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_rent
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_salary
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_quick_action_transfer
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_title
import org.jetbrains.compose.resources.stringResource

/**
 * Width the sample widget is laid out at before it is scaled into a picker tile: the dashboard's
 * own content width on a phone (a 392dp artboard minus its 16dp side padding). Measuring at the
 * real width is what makes the thumbnails comparable — see
 * [com.georgeci.moneysurfer.uikit.modifier.surferScaleToWidth].
 */
private val PREVIEW_CONTENT_WIDTH = 360.dp

/**
 * A widget drawn from sample data, the way [cardStyle] would draw it on the dashboard. The picker
 * shows these side by side, so they render the real widgets rather than a schematic: the whole
 * point of the choice is what the typography and the row density actually look like.
 *
 * The sample list is longer than a compact card shows on purpose — a size the user picks has to be
 * visibly different in the preview, and several widgets differ only in how many rows they keep.
 */
@Composable
internal fun DashboardWidgetPreview(
    type: DashboardWidgetType,
    cardStyle: DashboardCardStyle,
    modifier: Modifier = Modifier,
) {
    val widgetSize = when (cardStyle.size) {
        DashboardWidgetSize.Expanded -> SurferWidgetSize.Expanded
        DashboardWidgetSize.Compact -> SurferWidgetSize.Compact
    }
    CompositionLocalProvider(LocalSurferWidgetSize provides widgetSize) {
        val content = Modifier
            .surferScaleToWidth(PREVIEW_CONTENT_WIDTH)
            .fillMaxWidth()
        when (type) {
            DashboardWidgetType.Balance -> BalancePreview(cardStyle.variant, modifier.then(content))
            DashboardWidgetType.QuickActions -> QuickActionsPreview(modifier.then(content))
            DashboardWidgetType.Accounts -> AccountsPreview(modifier.then(content))
            DashboardWidgetType.Goals -> GoalsPreview(modifier.then(content))
            DashboardWidgetType.RecentTransactions -> RecentTransactionsPreview(modifier.then(content))
        }
    }
}

@Composable
private fun BalancePreview(variant: String?, modifier: Modifier) {
    SurferBalanceWidget(
        title = stringResource(Res.string.dashboard_balance_title),
        balance = SAMPLE_TOTAL,
        variant = SurferBalanceVariant.fromKey(variant),
        footnote = SurferBalanceFootnote.Trend(SAMPLE_TREND),
        modifier = modifier,
    )
}

/**
 * The buttons are inert here — the tile is a thumbnail, not a second place to log a transaction.
 *
 * The two tiles come out nearly the same height, because all this widget's size setting changes is
 * the button height (52dp against 48dp). That is the honest thumbnail: padding it out to make the
 * choice look bigger than it is would promise a row the dashboard then does not draw.
 */
@Composable
private fun QuickActionsPreview(modifier: Modifier) {
    SurferQuickActionsWidget(
        primaryLabel = stringResource(Res.string.dashboard_add_transaction),
        primaryIcon = SurferIcons.Add,
        onPrimaryClick = {},
        secondaryLabel = stringResource(Res.string.dashboard_quick_action_transfer),
        secondaryIcon = SurferIcons.SwapHoriz,
        onSecondaryClick = {},
        modifier = modifier,
    )
}

@Composable
private fun AccountsPreview(modifier: Modifier) {
    SurferAccountsWidget(
        items = listOf(
            SurferAccountItem(
                id = "preview-1",
                name = stringResource(Res.string.dashboard_customize_preview_account_everyday),
                subtitle = SAMPLE_CURRENCY,
                balance = SAMPLE_ACCOUNT_ONE,
            ),
            SurferAccountItem(
                id = "preview-2",
                name = stringResource(Res.string.dashboard_customize_preview_account_savings),
                subtitle = SAMPLE_CURRENCY,
                balance = SAMPLE_ACCOUNT_TWO,
            ),
            SurferAccountItem(
                id = "preview-3",
                name = stringResource(Res.string.dashboard_customize_preview_account_cash),
                subtitle = SAMPLE_CURRENCY,
                balance = SAMPLE_ACCOUNT_THREE,
            ),
        ),
        addCta = SurferAddAccountCta(
            label = stringResource(Res.string.dashboard_add_account),
            onClick = {},
            trailingLabel = stringResource(Res.string.dashboard_accounts_manage),
        ),
        modifier = modifier,
    )
}

@Composable
private fun GoalsPreview(modifier: Modifier) {
    SurferGoalsWidget(
        items = listOf(
            SurferGoalItem(
                id = "preview-1",
                name = stringResource(Res.string.dashboard_customize_preview_goal_laptop),
                savedFormatted = SAMPLE_GOAL_SAVED,
                targetFormatted = SAMPLE_GOAL_TARGET,
                progress = SAMPLE_GOAL_PROGRESS,
                captionLine = "",
            ),
            SurferGoalItem(
                id = "preview-2",
                name = stringResource(Res.string.dashboard_customize_preview_goal_trip),
                savedFormatted = SAMPLE_TRIP_SAVED,
                targetFormatted = SAMPLE_TRIP_TARGET,
                progress = SAMPLE_TRIP_PROGRESS,
                captionLine = "",
            ),
        ),
        title = stringResource(Res.string.dashboard_goals_title),
        seeAllLabel = stringResource(Res.string.dashboard_goals_see_all),
        onSeeAllClick = {},
        modifier = modifier,
    )
}

@Composable
private fun RecentTransactionsPreview(modifier: Modifier) {
    val bubbleBg = AppTheme.materialColors.primaryContainer
    val bubbleFg = AppTheme.materialColors.onPrimaryContainer
    val samples = listOf(
        stringResource(Res.string.dashboard_customize_preview_transaction_groceries) to SAMPLE_EXPENSE_ONE,
        stringResource(Res.string.dashboard_customize_preview_transaction_salary) to SAMPLE_INCOME,
        stringResource(Res.string.dashboard_customize_preview_transaction_coffee) to SAMPLE_EXPENSE_TWO,
        stringResource(Res.string.dashboard_customize_preview_transaction_rent) to SAMPLE_EXPENSE_THREE,
    )
    SurferRecentTransactionsWidget(
        items = samples.mapIndexed { index, (title, amount) ->
            SurferRecentTransactionItem(
                id = "preview-$index",
                title = title,
                subtitle = "",
                amount = amount,
                isExpense = amount != SAMPLE_INCOME,
                iconBgColor = bubbleBg,
                iconFgColor = bubbleFg,
                iconInitial = title.take(1),
                categoryHueSeed = "preview-$index",
            )
        },
        title = stringResource(Res.string.dashboard_recent_title),
        seeAllLabel = stringResource(Res.string.dashboard_recent_see_all),
        onSeeAllClick = {},
        modifier = modifier,
    )
}

private const val SAMPLE_CURRENCY = "EUR"
private const val SAMPLE_TOTAL = "€11,575.32"
private const val SAMPLE_TREND = "+€412"
private const val SAMPLE_ACCOUNT_ONE = "€2,480.32"
private const val SAMPLE_ACCOUNT_TWO = "€8,915.00"
private const val SAMPLE_ACCOUNT_THREE = "€180.00"
private const val SAMPLE_GOAL_SAVED = "€820"
private const val SAMPLE_GOAL_TARGET = "€1,600"
private const val SAMPLE_GOAL_PROGRESS = 0.51f
private const val SAMPLE_TRIP_SAVED = "€1,240"
private const val SAMPLE_TRIP_TARGET = "€3,000"
private const val SAMPLE_TRIP_PROGRESS = 0.41f
private const val SAMPLE_EXPENSE_ONE = "−€48.20"
private const val SAMPLE_INCOME = "+€3,200.00"
private const val SAMPLE_EXPENSE_TWO = "−€9.99"
private const val SAMPLE_EXPENSE_THREE = "−€760.00"
