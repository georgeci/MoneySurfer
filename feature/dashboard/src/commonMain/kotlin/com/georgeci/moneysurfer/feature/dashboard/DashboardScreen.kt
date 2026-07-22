package com.georgeci.moneysurfer.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.SurferSkeleton
import com.georgeci.moneysurfer.uikit.components.SurferSkeletonRow
import com.georgeci.moneysurfer.uikit.components.base.SurferDashboardToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.widgets.LocalSurferWidgetSize
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalItem
import com.georgeci.moneysurfer.uikit.widgets.SurferGoalsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_manage
import moneysurfer.feature.dashboard.generated.resources.dashboard_accounts_section_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_account_new
import moneysurfer.feature.dashboard.generated.resources.dashboard_add_transaction
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_empty_text
import moneysurfer.feature.dashboard.generated.resources.dashboard_balance_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_goals_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_empty_subtitle
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_empty_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_see_all
import moneysurfer.feature.dashboard.generated.resources.dashboard_recent_title
import moneysurfer.feature.dashboard.generated.resources.dashboard_settings_content_description
import moneysurfer.feature.dashboard.generated.resources.dashboard_toolbar_greeting
import moneysurfer.feature.dashboard.generated.resources.dashboard_toolbar_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the Dashboard — see docs/testing/testing-strategy.md. */
object DashboardTestTags {
    const val Root = "dashboard:root"
    const val Balance = "dashboard:balance"
    const val Settings = "dashboard:settings"
    const val AddTransaction = "dashboard:addTransaction"
}

@Composable
fun DashboardScreen(
    onNavigateToAccountCreation: () -> Unit,
    onNavigateToAccountsManage: () -> Unit,
    onNavigateToTransactionCreation: (accountId: AccountId?) -> Unit,
    onNavigateToAccountDetails: (AccountId) -> Unit,
    onNavigateToTransactionDetails: (TransactionId) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTransactionsList: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToGoalDetails: (GoalId) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAccountDetails -> onNavigateToAccountDetails(effect.accountId)
            is DashboardEffect.NavigateToTransactionDetails -> onNavigateToTransactionDetails(effect.transactionId)
            DashboardEffect.NavigateToAccountCreation -> onNavigateToAccountCreation()
            DashboardEffect.NavigateToAccountsManage -> onNavigateToAccountsManage()
            is DashboardEffect.NavigateToTransactionCreation -> onNavigateToTransactionCreation(effect.accountId)
            DashboardEffect.NavigateToSettings -> onNavigateToSettings()
            DashboardEffect.NavigateToTransactionsList -> onNavigateToTransactionsList()
            DashboardEffect.NavigateToGoals -> onNavigateToGoals()
            is DashboardEffect.NavigateToGoalDetails -> onNavigateToGoalDetails(effect.goalId)
        }
    }

    when (val current = state) {
        DashboardState.Loading -> DashboardLoading()
        is DashboardState.Content -> DashboardContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

private const val DASHBOARD_SKELETON_ROWS = 4

private val DASHBOARD_WIDGET_MIN_HEIGHT = 180.dp

@Composable
private fun DashboardLoading() {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
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

@Composable
private fun DashboardContent(
    state: DashboardState.Content,
    onEvent: (DashboardEvent) -> Unit,
) {
    val widgetSize = LocalSurferWidgetSize.current
    val heroWidgets = widgetSize == SurferWidgetSize.Hero
    val accountsPadding = if (heroWidgets) 8.dp else 6.dp

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
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.dashboard_add_transaction)) },
                    icon = {
                        // decorative — FAB text label provides the accessible label
                        Icon(imageVector = SurferIcons.Add, contentDescription = null)
                    },
                    onClick = { onEvent(DashboardEvent.OnAddTransactionClick) },
                    modifier = Modifier.testTag(DashboardTestTags.AddTransaction),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            item(key = "balance") {
                SurferBalanceWidget(
                    title = stringResource(Res.string.dashboard_balance_title),
                    balance = state.formattedTotalBalance ?: "—",
                    emptyText = if (state.formattedTotalBalance == null) {
                        stringResource(Res.string.dashboard_balance_empty_text)
                    } else {
                        null
                    },
                    trendText = state.formattedTrendDelta,
                    showSparkline = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
                        .testTag(DashboardTestTags.Balance),
                )
            }

            item(key = "accounts-header") {
                SectionHeader(
                    title = stringResource(Res.string.dashboard_accounts_section_title),
                    action = stringResource(Res.string.dashboard_accounts_manage),
                    onActionClick = { onEvent(DashboardEvent.OnManageAccountsClick) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item(key = "accounts") {
                SurferAccountsWidget(
                    items = state.accounts.map { it.toWidgetItem() },
                    onAddClick = { onEvent(DashboardEvent.OnAddAccountClick) },
                    addLabel = stringResource(Res.string.dashboard_add_account),
                    addCtaTrailingLabel = stringResource(Res.string.dashboard_add_account_new),
                    onItemClick = { item ->
                        item.accountId()?.let { onEvent(DashboardEvent.OnAccountClick(it)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
                        .padding(accountsPadding),
                )
            }

            item(key = "goals") {
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
                        .padding(vertical = 8.dp),
                )
            }

            item(key = "recent") {
                if (state.recentTransactionsEmpty) {
                    SurferEmptyState(
                        title = stringResource(Res.string.dashboard_recent_empty_title),
                        subtitle = stringResource(Res.string.dashboard_recent_empty_subtitle),
                        icon = SurferIcons.Receipt,
                        actionLabel = stringResource(Res.string.dashboard_add_transaction),
                        onActionClick = { onEvent(DashboardEvent.OnAddTransactionClick) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
                            .padding(vertical = 8.dp),
                    )
                } else {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .defaultMinSize(minHeight = DASHBOARD_WIDGET_MIN_HEIGHT)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onActionClick: (() -> Unit)? = null,
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
                modifier = if (onActionClick != null) {
                    Modifier.clickable(onClick = onActionClick)
                } else {
                    Modifier
                },
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

private fun TransactionUi.toWidgetItem(
    iconBgColor: Color,
    iconFgColor: Color,
): SurferRecentTransactionItem = SurferRecentTransactionItem(
    id = id.value,
    title = title,
    subtitle = "",
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
                formattedTrendDelta = "+€412 this month",
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
