package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferScaleToWidth
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferAddAccountCta
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceTrend
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferBudgetItem
import com.georgeci.moneysurfer.uikit.widgets.SurferBudgetsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateBar
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateData
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRatePace
import com.georgeci.moneysurfer.uikit.widgets.SurferBurnRateWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendCap
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendItem
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalItem
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightItem
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightTone
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferQuickActionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendData
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_manage
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_transaction
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_left
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_over
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_spent_of
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_ok
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_over
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_status_warn
import moneysurfer.feature.dashboard.generated.resources.dashboard_budgets_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_average
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_caption
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_on_track
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_projection
import moneysurfer.feature.dashboard.generated.resources.dashboard_burn_rate_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_cash
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_everyday
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_account_savings
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_goal_laptop
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_goal_trip
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_down_body
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_down_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_subs_body
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_subs_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_up_body
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_insight_up_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_coffee
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_groceries
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_rent
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_preview_transaction_salary
import moneysurfer.feature.dashboard.generated.resources.dashboard_days_left
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_insights_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_quick_action_transfer
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_caption
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_per_day
import moneysurfer.feature.dashboard.generated.resources.dashboard_safe_to_spend_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_of_cap
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_share
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_status_warn
import moneysurfer.feature.dashboard.generated.resources.dashboard_spent_by_category_title
import org.jetbrains.compose.resources.pluralStringResource
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
            DashboardWidgetType.SafeToSpend -> SafeToSpendPreview(modifier.then(content))
            DashboardWidgetType.BurnRate -> BurnRatePreview(modifier.then(content))
            DashboardWidgetType.Budgets -> BudgetsPreview(modifier.then(content))
            DashboardWidgetType.SpentByCategory ->
                SpentByCategoryPreview(cardStyle.variant, modifier.then(content))
            DashboardWidgetType.Accounts -> AccountsPreview(modifier.then(content))
            DashboardWidgetType.Insights -> InsightsPreview(cardStyle.variant, modifier.then(content))
            DashboardWidgetType.Goals -> GoalsPreview(modifier.then(content))
            DashboardWidgetType.RecentTransactions -> RecentTransactionsPreview(modifier.then(content))
        }
    }
}

/**
 * Sample curve as well as sample figures: half of what tells the six treatments apart is how much
 * room each gives the trend, and a tile drawn from a workspace with no history would show none of
 * that.
 */
@Composable
private fun BalancePreview(variant: String?, modifier: Modifier) {
    SurferBalanceWidget(
        title = stringResource(Res.string.dashboard_balance_title),
        balance = SAMPLE_TOTAL,
        variant = SurferBalanceVariant.fromKey(variant),
        trend = SurferBalanceTrend(text = SAMPLE_TREND, series = SAMPLE_BALANCE_SERIES),
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

/**
 * Sample numbers rather than the user's own: the tile is drawn for every widget in the picker,
 * including the ones switched off, so it must not depend on there being a budget to read.
 */
@Composable
private fun SafeToSpendPreview(modifier: Modifier) {
    SurferSafeToSpendWidget(
        title = stringResource(Res.string.dashboard_safe_to_spend_title),
        data = SurferSafeToSpendData(
            amount = SAMPLE_SAFE_TO_SPEND,
            caption = stringResource(
                Res.string.dashboard_safe_to_spend_caption,
                SAMPLE_BUDGET_LIMIT,
                stringResource(Res.string.dashboard_customize_preview_account_everyday),
            ),
            perDay = stringResource(Res.string.dashboard_safe_to_spend_per_day, SAMPLE_SAFE_PER_DAY),
            daysLeft = pluralStringResource(
                Res.plurals.dashboard_days_left,
                SAMPLE_DAYS_LEFT,
                SAMPLE_DAYS_LEFT,
            ),
            progress = SAMPLE_SAFE_PROGRESS,
            paceFraction = SAMPLE_SAFE_PACE,
            status = SurferBudgetStatus.Ok,
        ),
        modifier = modifier,
    )
}

/**
 * Sample rows, like [SafeToSpendPreview] — the tile is drawn for every widget in the picker,
 * including the ones switched off, so it must not depend on the month holding any spend.
 *
 * One of the three carries a cap, because the near-limit treatment is half of what distinguishes
 * the five variants from each other.
 */
@Composable
private fun SpentByCategoryPreview(variant: String?, modifier: Modifier) {
    val tints = SurferCategoryPalette.tints
    SurferSpentByCategoryWidget(
        title = stringResource(Res.string.dashboard_spent_by_category_title),
        items = listOf(
            SurferCategorySpendItem(
                id = "preview-rent",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_rent),
                amount = SAMPLE_EXPENSE_THREE_MAGNITUDE,
                share = SAMPLE_SHARE_RENT,
                caption = stringResource(Res.string.dashboard_spent_by_category_share, SAMPLE_PERCENT_RENT),
                tint = tints[0],
            ),
            SurferCategorySpendItem(
                id = "preview-groceries",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_groceries),
                amount = SAMPLE_CATEGORY_GROCERIES,
                share = SAMPLE_SHARE_GROCERIES,
                caption = stringResource(
                    Res.string.dashboard_spent_by_category_of_cap,
                    SAMPLE_CATEGORY_GROCERIES,
                    SAMPLE_CATEGORY_CAP,
                ),
                tint = tints[1],
                cap = SurferCategorySpendCap(
                    progress = SAMPLE_CAP_PROGRESS,
                    status = SurferBudgetStatus.Warn,
                    statusLabel = stringResource(Res.string.dashboard_spent_by_category_status_warn),
                ),
            ),
            SurferCategorySpendItem(
                id = "preview-coffee",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_coffee),
                amount = SAMPLE_CATEGORY_COFFEE,
                share = SAMPLE_SHARE_COFFEE,
                caption = stringResource(Res.string.dashboard_spent_by_category_share, SAMPLE_PERCENT_COFFEE),
                tint = tints[2],
            ),
        ),
        variant = SurferSpentByCategoryVariant.fromKey(variant),
        modifier = modifier,
    )
}

/**
 * Sample numbers again, for the same reason [SafeToSpendPreview] uses them: the tile is drawn for
 * every widget in the picker, including the ones switched off, so it must not depend on there being
 * a week of spend to read.
 */
@Composable
private fun BurnRatePreview(modifier: Modifier) {
    SurferBurnRateWidget(
        title = stringResource(Res.string.dashboard_burn_rate_title),
        data = SurferBurnRateData(
            average = stringResource(Res.string.dashboard_burn_rate_average, SAMPLE_BURN_AVERAGE),
            caption = stringResource(Res.string.dashboard_burn_rate_caption),
            bars = SAMPLE_BURN_FRACTIONS.mapIndexed { index, fraction ->
                SurferBurnRateBar(
                    label = (SAMPLE_BURN_FIRST_DAY + index).toString(),
                    fraction = fraction,
                    isToday = index == SAMPLE_BURN_FRACTIONS.lastIndex,
                )
            },
            projection = stringResource(Res.string.dashboard_burn_rate_projection, SAMPLE_BURN_PROJECTION),
            pace = SurferBurnRatePace(
                label = stringResource(Res.string.dashboard_burn_rate_on_track),
                status = SurferBudgetStatus.Ok,
            ),
        ),
        modifier = modifier,
    )
}

/**
 * Sample budgets rather than the user's own, for the same reason the safe-to-spend tile uses them:
 * the picker draws every widget, including the ones switched off, so it must not depend on there
 * being a budget to read. The ids are named rather than numbered — nothing reads them back, and
 * repeating "preview-1" across widgets is a duplicated literal for no gain.
 */
@Composable
private fun BudgetsPreview(modifier: Modifier) {
    SurferBudgetsWidget(
        items = listOf(
            SurferBudgetItem(
                id = "preview-budget-groceries",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_groceries),
                statusLabel = stringResource(Res.string.dashboard_budgets_status_warn),
                status = SurferBudgetStatus.Warn,
                spentOfLimit = stringResource(
                    Res.string.dashboard_budgets_spent_of,
                    SAMPLE_BUDGET_SPENT,
                    SAMPLE_BUDGET_CAP,
                ),
                remaining = stringResource(Res.string.dashboard_budgets_left, SAMPLE_BUDGET_LEFT),
                progress = SAMPLE_BUDGET_PROGRESS,
                alertFraction = SAMPLE_BUDGET_ALERT,
            ),
            SurferBudgetItem(
                id = "preview-budget-rent",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_rent),
                statusLabel = stringResource(Res.string.dashboard_budgets_status_ok),
                status = SurferBudgetStatus.Ok,
                spentOfLimit = stringResource(
                    Res.string.dashboard_budgets_spent_of,
                    SAMPLE_RENT_SPENT,
                    SAMPLE_RENT_CAP,
                ),
                remaining = stringResource(Res.string.dashboard_budgets_left, SAMPLE_RENT_LEFT),
                progress = SAMPLE_RENT_PROGRESS,
                alertFraction = SAMPLE_BUDGET_ALERT,
            ),
            SurferBudgetItem(
                id = "preview-budget-coffee",
                name = stringResource(Res.string.dashboard_customize_preview_transaction_coffee),
                statusLabel = stringResource(Res.string.dashboard_budgets_status_over),
                status = SurferBudgetStatus.Over,
                spentOfLimit = stringResource(
                    Res.string.dashboard_budgets_spent_of,
                    SAMPLE_COFFEE_SPENT,
                    SAMPLE_COFFEE_CAP,
                ),
                remaining = stringResource(Res.string.dashboard_budgets_over, SAMPLE_COFFEE_OVER),
                progress = SAMPLE_COFFEE_PROGRESS,
                alertFraction = SAMPLE_BUDGET_ALERT,
            ),
        ),
        title = stringResource(Res.string.dashboard_budgets_title),
        seeAllLabel = stringResource(Res.string.dashboard_budgets_see_all),
        onSeeAllClick = {},
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

/**
 * Sample sentences rather than the user's own, like every other tile here: the picker is opened
 * to compare two layouts, and a workspace with one insight (or none) would show two blank tiles.
 */
@Composable
private fun InsightsPreview(variant: String?, modifier: Modifier) {
    SurferInsightsWidget(
        items = listOf(
            SurferInsightItem(
                id = "preview-insight-1",
                tone = SurferInsightTone.Warn,
                icon = SurferIcons.ArrowUp,
                title = stringResource(Res.string.dashboard_customize_preview_insight_up_title),
                body = stringResource(Res.string.dashboard_customize_preview_insight_up_body),
            ),
            SurferInsightItem(
                id = "preview-insight-2",
                tone = SurferInsightTone.Good,
                icon = SurferIcons.ArrowDown,
                title = stringResource(Res.string.dashboard_customize_preview_insight_down_title),
                body = stringResource(Res.string.dashboard_customize_preview_insight_down_body),
            ),
            SurferInsightItem(
                id = "preview-insight-3",
                tone = SurferInsightTone.Neutral,
                icon = SurferIcons.Sync,
                title = stringResource(Res.string.dashboard_customize_preview_insight_subs_title),
                body = stringResource(Res.string.dashboard_customize_preview_insight_subs_body),
            ),
        ),
        title = stringResource(Res.string.dashboard_insights_title),
        variant = SurferInsightsVariant.fromKey(variant),
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
private val SAMPLE_BALANCE_SERIES = listOf(9_800f, 10_240f, 9_950f, 10_610f, 11_160f, 11_575f)
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
private const val SAMPLE_SAFE_TO_SPEND = "€642.30"
private const val SAMPLE_BUDGET_LIMIT = "€1,800"
private const val SAMPLE_SAFE_PER_DAY = "€53.52"
private const val SAMPLE_DAYS_LEFT = 12
private const val SAMPLE_SAFE_PROGRESS = 0.64f
private const val SAMPLE_SAFE_PACE = 0.6f
private const val SAMPLE_BURN_AVERAGE = "€42.10"
private const val SAMPLE_BURN_PROJECTION = "€1,263"
private const val SAMPLE_BURN_FIRST_DAY = 22
private val SAMPLE_BURN_FRACTIONS = listOf(0.42f, 0.18f, 1f, 0f, 0.63f, 0.31f, 0.24f)
private const val SAMPLE_BUDGET_SPENT = "€312.40"
private const val SAMPLE_BUDGET_CAP = "€400.00"
private const val SAMPLE_BUDGET_LEFT = "€87.60"
private const val SAMPLE_BUDGET_PROGRESS = 0.78f
private const val SAMPLE_BUDGET_ALERT = 0.8f
private const val SAMPLE_RENT_SPENT = "€760.00"
private const val SAMPLE_RENT_CAP = "€1,200.00"
private const val SAMPLE_RENT_LEFT = "€440.00"
private const val SAMPLE_RENT_PROGRESS = 0.63f
private const val SAMPLE_COFFEE_SPENT = "€68.40"
private const val SAMPLE_COFFEE_CAP = "€60.00"
private const val SAMPLE_COFFEE_OVER = "€8.40"
private const val SAMPLE_COFFEE_PROGRESS = 1.14f
private const val SAMPLE_EXPENSE_THREE_MAGNITUDE = "€760.00"
private const val SAMPLE_CATEGORY_GROCERIES = "€142.10"
private const val SAMPLE_CATEGORY_COFFEE = "€38.20"
private const val SAMPLE_CATEGORY_CAP = "€150"
private const val SAMPLE_SHARE_RENT = 0.62f
private const val SAMPLE_SHARE_GROCERIES = 0.25f
private const val SAMPLE_SHARE_COFFEE = 0.13f
private const val SAMPLE_PERCENT_RENT = 62
private const val SAMPLE_PERCENT_COFFEE = 13
private const val SAMPLE_CAP_PROGRESS = 0.95f
