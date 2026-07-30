package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.SurferSkeleton
import com.georgeci.moneysurfer.uikit.components.SurferSkeletonRow
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferDashboardToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferAddAccountCta
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalItem
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferQuickActionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_manage
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_section_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account_new
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_transaction
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_content_description
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_quick_action_transfer
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_split_categories
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_settings_content_description
import moneysurfer.feature.dashboard.generated.resources.dashboard_toolbar_greeting
import moneysurfer.feature.dashboard.generated.resources.dashboard_toolbar_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stable selectors for the Dashboard — see docs/testing/testing-strategy.md.
 *
 * The widget tags name the section, not the composable: which widgets are present and in what
 * order is user configuration, so a flow asserts [Accounts], [Recent] or [Goals] instead of
 * an index into the column. [AddAccount], [RecentSeeAll], [GoalsSeeAll] and [RecentEmpty] sit
 * on nodes owned by shared `uikit` widgets, which take the tag as a parameter.
 */
object DashboardTestTags {
    const val Root = "dashboard:root"
    const val Balance = "dashboard:balance"
    const val Customize = "dashboard:customize"
    const val Settings = "dashboard:settings"
    const val AddTransaction = "dashboard:addTransaction"

    /** The Week/Month switch itself; [periodOption] addresses either of its two segments. */
    const val PeriodSwitch = "dashboard:period"

    fun periodOption(period: DashboardPeriod): String = "$PeriodSwitch:${period.name.lowercase()}"

    /**
     * The quick-actions row, not either button inside it — `SurferQuickActionsWidget` takes no per
     * button tag, and reusing [AddTransaction] for its primary action would put two nodes under one
     * id, which is an ambiguous selector rather than a convenience. Its buttons are reachable by
     * their labels, both of which this screen owns.
     */
    const val QuickActions = "dashboard:quickActions"
    const val SafeToSpend = "dashboard:safeToSpend"

    /** The "Set a budget" link, shown only while the safe-to-spend widget has no budget to read. */
    const val SafeToSpendSetBudget = "dashboard:safeToSpendSetBudget"
    const val BurnRate = "dashboard:burnRate"
    const val Budgets = "dashboard:budgets"
    const val BudgetsSeeAll = "dashboard:budgetsSeeAll"

    /** The whole spent-by-category card, whichever of its five treatments is selected. */
    const val SpentByCategory = "dashboard:spentByCategory"
    const val Accounts = "dashboard:accounts"

    /**
     * Empty-state CTA only — `SurferAccountsWidget` composes it just while the account list is
     * empty. Once an account exists, new ones are created from [AccountsManage].
     */
    const val AddAccount = "dashboard:addAccount"
    const val AccountsManage = "dashboard:accountsManage"
    const val Insights = "dashboard:insights"
    const val Recent = "dashboard:recent"
    const val RecentSeeAll = "dashboard:recentSeeAll"
    const val RecentEmpty = "dashboard:recentEmpty"
    const val Goals = "dashboard:goals"
    const val GoalsSeeAll = "dashboard:goalsSeeAll"
}

@Composable
fun DashboardScreen(
    navigation: DashboardNavigation,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    HandleDashboardEffects(viewModel, navigation)

    when (val current = state) {
        DashboardState.Loading -> DashboardLoading()
        is DashboardState.Content -> DashboardContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

private const val DASHBOARD_SKELETON_ROWS = 4

/** Floor the list-shaped cards share, so a short list does not collapse the column's rhythm. */
internal val DASHBOARD_WIDGET_MIN_HEIGHT = 180.dp

/** Bottom scroll inset that keeps the last row clear of the extended "Add transaction" FAB. */
private val DASHBOARD_FAB_CLEARANCE = 88.dp

/** A transfer needs somewhere to send the money, so the quick-actions row waits for a second account. */
private const val MIN_ACCOUNTS_FOR_TRANSFER = 2

@Composable
private fun DashboardLoading() {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(180.dp),
            )
            repeat(DASHBOARD_SKELETON_ROWS) {
                SurferSkeletonRow()
            }
        }
    }
}

/**
 * Stateless half of the screen — public so `:composeApp` desktop UI tests can mount it with an
 * injected state, the way `DashboardCustomizeContent` is used.
 */
@Composable
fun DashboardContent(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .surferTestTagAsId()
            .testTag(DashboardTestTags.Root),
        topBar = {
            val defaultTitle = stringResource(Res.string.dashboard_toolbar_title)
            val workspaceName = state.workspaceName ?: defaultTitle
            SurferDashboardToolbar(
                letter = state.workspaceInitial
                    ?: workspaceName.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                primaryText = workspaceName,
                secondaryText = state.greeting ?: stringResource(Res.string.dashboard_toolbar_greeting),
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Edit,
                        contentDescription = stringResource(Res.string.dashboard_customize_content_description),
                        onClick = { onEvent(DashboardEvent.OnCustomizeClick) },
                        modifier = Modifier.testTag(DashboardTestTags.Customize),
                    )
                    SurferToolbarAction(
                        icon = SurferIcons.Settings,
                        contentDescription = stringResource(Res.string.dashboard_settings_content_description),
                        onClick = { onEvent(DashboardEvent.OnSettingsClick) },
                        modifier = Modifier.testTag(DashboardTestTags.Settings),
                    )
                },
            )
        },
        floatingActionButton = {
            if (state.accounts.isNotEmpty()) {
                SurferAddFab(
                    label = stringResource(Res.string.dashboard_add_transaction),
                    onClick = { onEvent(DashboardEvent.OnAddTransactionClick) },
                    modifier = Modifier.testTag(DashboardTestTags.AddTransaction),
                )
            }
        },
    ) { padding ->
        // Clear the extended FAB so the last transactions can scroll above it instead of
        // being hidden behind it; only reserve the space when the FAB is actually shown.
        val fabClearance = if (state.accounts.isNotEmpty()) DASHBOARD_FAB_CLEARANCE else 0.dp
        DashboardWidgets(
            state = state,
            topInset = padding.calculateTopPadding(),
            bottomInset = padding.calculateBottomPadding() + fabClearance,
            onEvent = onEvent,
        )
    }
}

/**
 * Whether [type] would draw anything at all for this state.
 *
 * A widget that stands itself down costs nothing in the single column — a zero-height row nobody
 * sees. In the grid it still claims the columns its span asked for, so the row it sits in gets a
 * hole. The layout therefore skips these before placing anything, and the widget itself keeps the
 * same guard so the two can never disagree about what is on screen.
 *
 * Only quick actions has such a state today; every other widget draws its own empty treatment.
 */
internal fun DashboardState.Content.rendersWidget(type: DashboardWidgetType): Boolean = when (type) {
    DashboardWidgetType.QuickActions -> transferEnabled && accounts.size >= MIN_ACCOUNTS_FOR_TRANSFER
    else -> true
}

/**
 * The registry's render half: every [DashboardWidgetType] resolves to exactly one widget here.
 * Adding a widget type without a branch is a compile error, which is the point.
 *
 * [variant] is the widget-specific half of the card style. It stays a raw key this far in: only
 * the widget that defines the treatments knows how to read it, and a widget with none ignores it.
 */
@Composable
internal fun DashboardWidget(
    type: DashboardWidgetType,
    variant: String?,
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    when (type) {
        DashboardWidgetType.Balance -> BalanceWidget(state, variant)
        DashboardWidgetType.QuickActions -> QuickActionsWidget(state, onEvent)
        DashboardWidgetType.SafeToSpend -> SafeToSpendWidget(state, onEvent)
        DashboardWidgetType.BurnRate -> BurnRateWidget(state)
        DashboardWidgetType.Budgets -> BudgetsWidget(state, onEvent)
        DashboardWidgetType.SpentByCategory -> SpentByCategoryWidget(state, variant)
        DashboardWidgetType.Accounts -> AccountsWidget(state, onEvent)
        DashboardWidgetType.Insights -> InsightsWidget(state, variant)
        DashboardWidgetType.Goals -> GoalsWidget(state, onEvent)
        DashboardWidgetType.RecentTransactions -> RecentTransactionsWidget(state, onEvent)
    }
}

/**
 * The two shortcuts that skip the FAB: log a transaction, or move money between accounts.
 *
 * Stands down entirely rather than offering an action the screen would refuse:
 * - with `transferEnabled` off the creation screen ignores the type switch, so a Transfer button
 *   would land on an expense form — and this row is half Transfer button;
 * - below [MIN_ACCOUNTS_FOR_TRANSFER] there is nothing to move money between. The form would open
 *   with an empty "To" slot whose picker excludes the only account there is, leaving Save disabled
 *   with no way to enable it.
 *
 * All or nothing, because the widget draws both buttons or neither — and the half worth keeping is
 * Transfer, since "Add transaction" is the FAB this screen already shows.
 */
@Composable
private fun QuickActionsWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    if (!state.rendersWidget(DashboardWidgetType.QuickActions)) return
    SurferQuickActionsWidget(
        primaryLabel = stringResource(Res.string.dashboard_add_transaction),
        primaryIcon = SurferIcons.Add,
        onPrimaryClick = { onEvent(DashboardEvent.OnAddTransactionClick) },
        secondaryLabel = stringResource(Res.string.dashboard_quick_action_transfer),
        secondaryIcon = SurferIcons.SwapHoriz,
        onSecondaryClick = { onEvent(DashboardEvent.OnTransferClick) },
        // No explicit height: SurferButton pins its own (52dp Biggest, 48dp Regular) and the row is
        // top-aligned, so a taller box would just hang dead space under the buttons.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.QuickActions),
    )
}

@Composable
private fun AccountsWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    val hero = LocalSurferWidgetSize.current == SurferWidgetSize.Expanded
    Column(modifier = Modifier.testTag(DashboardTestTags.Accounts)) {
        SectionHeader(
            title = stringResource(Res.string.dashboard_accounts_section_title),
            action = stringResource(Res.string.dashboard_accounts_manage),
            onActionClick = { onEvent(DashboardEvent.OnManageAccountsClick) },
            actionTestTag = DashboardTestTags.AccountsManage,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        SurferAccountsWidget(
            items = state.accounts.map { it.toWidgetItem() },
            addCta = SurferAddAccountCta(
                label = stringResource(Res.string.dashboard_add_account),
                onClick = { onEvent(DashboardEvent.OnAddAccountClick) },
                trailingLabel = stringResource(Res.string.dashboard_add_account_new),
                testTag = DashboardTestTags.AddAccount,
            ),
            onItemClick = { item ->
                item.accountId()?.let { onEvent(DashboardEvent.OnAccountClick(it)) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(vertical = if (hero) 8.dp else 6.dp),
        )
    }
}

@Composable
private fun GoalsWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    SurferGoalsWidget(
        items = state.goals.map { it.toWidgetItem() },
        title = stringResource(Res.string.dashboard_goals_title),
        seeAllLabel = stringResource(Res.string.dashboard_goals_see_all),
        onSeeAllClick = { onEvent(DashboardEvent.OnSeeAllGoalsClick) },
        onItemClick = { item -> onEvent(DashboardEvent.OnGoalClick(GoalId(item.id))) },
        emptyTitle = stringResource(Res.string.dashboard_goals_empty_title),
        emptySubtitle = stringResource(Res.string.dashboard_goals_empty_subtitle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
            .padding(vertical = 8.dp)
            .testTag(DashboardTestTags.Goals),
        seeAllTestTag = DashboardTestTags.GoalsSeeAll,
    )
}

@Composable
private fun RecentTransactionsWidget(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
        .padding(vertical = 8.dp)
        .testTag(DashboardTestTags.Recent)

    val bubbleBg = AppTheme.materialColors.primaryContainer
    val bubbleFg = AppTheme.materialColors.onPrimaryContainer
    SurferRecentTransactionsWidget(
        items = state.transactions.map { it.toWidgetItem(bubbleBg, bubbleFg) },
        title = stringResource(Res.string.dashboard_recent_title),
        seeAllLabel = stringResource(Res.string.dashboard_recent_see_all),
        onSeeAllClick = { onEvent(DashboardEvent.OnSeeAllTransactionsClick) },
        onItemClick = { item ->
            item.transactionId()?.let { onEvent(DashboardEvent.OnTransactionClick(it)) }
        },
        emptyTitle = stringResource(Res.string.dashboard_recent_empty_title),
        emptySubtitle = stringResource(Res.string.dashboard_recent_empty_subtitle),
        modifier = modifier,
        seeAllTestTag = DashboardTestTags.RecentSeeAll,
        emptyTestTag = DashboardTestTags.RecentEmpty,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionTestTag: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = (
                    if (onActionClick != null) {
                        Modifier.clickable(onClick = onActionClick)
                    } else {
                        Modifier
                    }
                    ).then(actionTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            )
        }
    }
}

private fun AccountUi.toWidgetItem(): SurferAccountItem = SurferAccountItem(
    id = id.value,
    name = name,
    subtitle = currency,
    balance = formattedBalance,
)

/** The widget's caption line stays empty in v1 — the ETA copy ships with the forecast work. */
private fun GoalUi.toWidgetItem(): SurferGoalItem = SurferGoalItem(
    id = id.value,
    name = name,
    savedFormatted = formattedSaved,
    targetFormatted = formattedTarget,
    progress = progress,
    captionLine = "",
)

private fun SurferAccountItem.accountId(): AccountId? =
    id.takeIf { it.isNotEmpty() }?.let(::AccountId)

@Composable
private fun TransactionUi.toWidgetItem(
    iconBgColor: Color,
    iconFgColor: Color,
): SurferRecentTransactionItem = SurferRecentTransactionItem(
    id = id.value,
    title = title,
    // A collapsed receipt says how many categories it covers; an ordinary row has nothing to add
    // under its title here, and the list screen is where the category belongs.
    subtitle = if (splitCategoryCount > 0) {
        stringResource(Res.string.dashboard_recent_split_categories, splitCategoryCount)
    } else {
        ""
    },
    amount = formattedAmount,
    isExpense = isExpense,
    iconBgColor = iconBgColor,
    iconFgColor = iconFgColor,
    iconInitial = title.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
    categoryHueSeed = categoryHueSeed,
)

private fun SurferRecentTransactionItem.transactionId(): TransactionId? =
    id.takeIf { it.isNotEmpty() }?.let(::TransactionId)

@Preview(heightDp = 1600)
@Composable
private fun DashboardScreenPreview() {
    AppTheme {
        DashboardContent(
            state = DashboardState.Content(
                accounts = listOf(
                    AccountUi(AccountId("preview-acc-1"), "Everyday", "€2,480.32", "EUR"),
                    AccountUi(AccountId("preview-acc-2"), "Emergency Fund", "€8,915.00", "EUR"),
                ),
                transactions = listOf(
                    TransactionUi(
                        id = TransactionId("preview-tx-1"),
                        title = "Lidl — weekly shop",
                        formattedAmount = "−€48.20",
                        isExpense = true,
                        categoryHueSeed = "preview-cat-1",
                    ),
                    TransactionUi(
                        id = TransactionId("preview-tx-2"),
                        title = "March payroll",
                        formattedAmount = "+€3,200.00",
                        isExpense = false,
                        categoryHueSeed = "preview-cat-2",
                    ),
                    TransactionUi(
                        id = TransactionId("preview-tx-3"),
                        title = "Coffee",
                        formattedAmount = "−€9.99",
                        isExpense = true,
                        categoryHueSeed = "preview-cat-3",
                    ),
                ),
                formattedTotalBalance = "€11,575.32",
                workspaceName = null,
                workspaceInitial = null,
                greeting = null,
                formattedTrendDelta = "+€412",
                balanceSeries = listOf(9_800f, 10_240f, 9_950f, 10_610f, 11_160f, 11_575f),
                transferEnabled = true,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun DashboardScreenEmptyPreview() {
    AppTheme {
        DashboardContent(
            state = DashboardState.Content(
                accounts = emptyList(),
                transactions = emptyList(),
                formattedTotalBalance = null,
                workspaceName = null,
                workspaceInitial = null,
                greeting = null,
                formattedTrendDelta = null,
            ),
            onEvent = {},
        )
    }
}
