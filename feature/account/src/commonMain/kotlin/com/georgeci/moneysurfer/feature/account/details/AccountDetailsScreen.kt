package com.georgeci.moneysurfer.feature.account.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.account.extraDetailLabel
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_add_transaction
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_chart_title
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_edit_content_description
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_extra_details_section
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_filter_all
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_filter_expenses
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_filter_income
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_in_this_month
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_more_content_description
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_out_this_month
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_title
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_transactions_empty
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_transactions_section
import com.georgeci.moneysurfer.feature.account.generated.resources.account_details_transactions_see_all
import com.georgeci.moneysurfer.feature.account.icon
import com.georgeci.moneysurfer.feature.account.isMonospaceExtraDetail
import com.georgeci.moneysurfer.feature.account.labelRes
import com.georgeci.moneysurfer.uikit.components.SurferSkeletonRow
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountDetailsHeroCard
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountStatCard
import com.georgeci.moneysurfer.uikit.components.account.SurferBalanceChartCard
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferFilterChipRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the account details screen — see docs/testing/testing-strategy.md. */
object AccountDetailsTestTags {
    const val Root = "accountDetails:root"
    const val SeeAll = "accountDetails:seeAll"

    /** Transaction-type filter chip, suffixed with the lowercased [TransactionFilter]. */
    const val FilterPrefix = "accountDetails:filter:"
}

@Composable
fun AccountDetailsScreen(
    accountId: AccountId,
    onNavigateBack: () -> Unit,
    onNavigateToTransactionCreation: (accountId: AccountId) -> Unit = {},
    onNavigateToTransactionDetails: (transactionId: TransactionId) -> Unit = {},
    onNavigateToAccountEdit: (accountId: AccountId) -> Unit = {},
    onNavigateToTransactionsList: (accountId: AccountId) -> Unit = {},
    viewModel: AccountDetailsViewModel = koinViewModel(key = accountId.value) { parametersOf(accountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            AccountDetailsEffect.NavigateBack -> onNavigateBack()
            is AccountDetailsEffect.NavigateToTransactionDetails -> onNavigateToTransactionDetails(effect.transactionId)
            AccountDetailsEffect.NavigateToTransactionCreation -> onNavigateToTransactionCreation(accountId)
            is AccountDetailsEffect.NavigateToAccountEdit -> onNavigateToAccountEdit(effect.accountId)
            is AccountDetailsEffect.NavigateToTransactionsList -> onNavigateToTransactionsList(effect.accountId)
        }
    }

    when (val current = state) {
        is AccountDetailsState.Loading -> AccountDetailsLoading(onEvent = viewModel::onEvent)
        is AccountDetailsState.Content -> AccountDetailsContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun AccountDetailsLoading(onEvent: (AccountDetailsEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.account_details_title),
                onBack = { onEvent(AccountDetailsEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.default, vertical = AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            repeat(SKELETON_ROWS) {
                SurferSkeletonRow()
            }
        }
    }
}

private const val SKELETON_ROWS = 5

@Composable
private fun AccountDetailsContent(
    state: AccountDetailsState.Content,
    onEvent: (AccountDetailsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(AccountDetailsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.account_details_title),
                onBack = { onEvent(AccountDetailsEvent.OnBackClick) },
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Edit,
                        contentDescription = stringResource(Res.string.account_details_edit_content_description),
                        onClick = { onEvent(AccountDetailsEvent.OnEditClick) },
                    )
                    SurferToolbarAction(
                        icon = SurferIcons.MoreVert,
                        contentDescription = stringResource(Res.string.account_details_more_content_description),
                        onClick = { /* overflow menu — wires later */ },
                    )
                },
            )
        },
        floatingActionButton = {
            SurferAddFab(
                label = stringResource(Res.string.account_details_add_transaction),
                onClick = { onEvent(AccountDetailsEvent.OnAddTransactionClick) },
            )
        },
    ) { padding ->
        val filtered = remember(state.filter, state.transactions) {
            when (state.filter) {
                TransactionFilter.All -> state.transactions
                TransactionFilter.Expenses -> state.transactions.filter { it.isExpense }
                TransactionFilter.Income -> state.transactions.filterNot { it.isExpense }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            item {
                SurferAccountDetailsHeroCard(
                    // Falls back to the generic wallet only while the type is still loading.
                    icon = state.type?.icon() ?: SurferIcons.Wallet,
                    name = state.name,
                    // The mockup's meta line is the account kind ("CURRENT · •• 4021"); the
                    // last-4 half needs a field the model does not have yet, so only the kind
                    // shows — still closer than the currency code that used to sit here.
                    subtitle = state.type?.let { stringResource(it.labelRes()).uppercase() }.orEmpty(),
                    formattedBalance = state.formattedBalance.ifBlank { "€0.00" },
                    modifier = Modifier
                        .padding(horizontal = AppTheme.spacing.default)
                        .padding(bottom = AppTheme.spacing.large),
                )
            }
            item { QuickStats(state) }
            item {
                SurferBalanceChartCard(
                    title = stringResource(Res.string.account_details_chart_title),
                    delta = state.chart.formattedDelta,
                    deltaColor = if (state.chart.isDeltaNegative) {
                        AppTheme.materialColors.error
                    } else {
                        AppTheme.semanticColors.income
                    },
                    points = state.chart.points,
                    modifier = Modifier
                        .padding(horizontal = AppTheme.spacing.default)
                        .padding(bottom = AppTheme.spacing.medium),
                )
            }
            if (state.extraDetails.isNotEmpty()) {
                item { ExtraDetailsSection(details = state.extraDetails) }
            }
            item {
                FilterChips(
                    selected = state.filter,
                    onSelect = { onEvent(AccountDetailsEvent.OnFilterChanged(it)) },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppTheme.spacing.large,
                            end = AppTheme.spacing.large,
                            top = AppTheme.spacing.medium,
                            bottom = AppTheme.spacing.xSmall,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.account_details_transactions_section),
                        style = AppTheme.typography.labelLarge,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(Res.string.account_details_transactions_see_all),
                        style = AppTheme.typography.labelLarge,
                        color = AppTheme.materialColors.primary,
                        modifier = Modifier
                            .testTag(AccountDetailsTestTags.SeeAll)
                            .clickable {
                                onEvent(AccountDetailsEvent.OnSeeAllTransactionsClick)
                            },
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.account_details_transactions_empty),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        modifier = Modifier.padding(AppTheme.spacing.large),
                    )
                }
            } else {
                items(filtered, key = { it.id.value }) { transaction ->
                    val incomeColor = AppTheme.semanticColors.income
                    SurferTransactionLine(
                        icon = if (transaction.isExpense) SurferIcons.Receipt else SurferIcons.Wallet,
                        title = transaction.title,
                        formattedAmount = transaction.formattedAmount,
                        categoryHueSeed = transaction.categoryHueSeed,
                        amountColor = if (transaction.isExpense) {
                            AppTheme.materialColors.onSurface
                        } else {
                            incomeColor
                        },
                        amountPillBackground = if (transaction.isExpense) {
                            null
                        } else {
                            incomeColor.copy(alpha = 0.18f)
                        },
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.default)
                            .padding(bottom = AppTheme.spacing.small),
                        onClick = { onEvent(AccountDetailsEvent.OnTransactionClick(transaction.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStats(state: AccountDetailsState.Content) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.default)
            .padding(bottom = AppTheme.spacing.default),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        SurferAccountStatCard(
            label = stringResource(Res.string.account_details_in_this_month),
            value = state.formattedIncome,
            icon = SurferIcons.ArrowDown,
            iconTint = AppTheme.semanticColors.income,
            modifier = Modifier.weight(1f),
        )
        SurferAccountStatCard(
            label = stringResource(Res.string.account_details_out_this_month),
            value = state.formattedExpenses,
            icon = SurferIcons.ArrowUp,
            iconTint = AppTheme.materialColors.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The "Extra details" the creation screen collected. Read-only here — editing them means opening
 * the edit form, which is the same screen that defined them.
 */
@Composable
private fun ExtraDetailsSection(details: List<AccountExtraDetail>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large)
            .padding(bottom = AppTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(Res.string.account_details_extra_details_section),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        details.forEach { detail ->
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xSmall)) {
                Text(
                    text = extraDetailLabel(detail.key),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                Text(
                    text = detail.value,
                    style = if (isMonospaceExtraDetail(detail.key)) {
                        AppTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        AppTheme.typography.bodyMedium
                    },
                    color = AppTheme.materialColors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FilterChips(
    selected: TransactionFilter,
    onSelect: (TransactionFilter) -> Unit,
) {
    SurferFilterChipRow(
        modifier = Modifier.padding(horizontal = AppTheme.spacing.default),
        options = listOf(TransactionFilter.All, TransactionFilter.Expenses, TransactionFilter.Income),
        selected = selected,
        label = { filter ->
            stringResource(
                when (filter) {
                    TransactionFilter.All -> Res.string.account_details_filter_all
                    TransactionFilter.Expenses -> Res.string.account_details_filter_expenses
                    TransactionFilter.Income -> Res.string.account_details_filter_income
                },
            )
        },
        onSelect = onSelect,
        optionTestTag = { filter -> AccountDetailsTestTags.FilterPrefix + filter.name.lowercase() },
    )
}

/** A month of plausible balances so the preview draws the same shape the real series does. */
private val PreviewChartPoints: List<Pair<Float, Float>> = listOf(
    2068f, 2042f, 2110f, 2095f, 2180f, 2164f, 2140f, 2210f, 2196f, 2255f,
    2231f, 2288f, 2262f, 2240f, 2310f, 2295f, 2352f, 2330f, 2308f, 2374f,
    2360f, 2412f, 2390f, 2368f, 2430f, 2415f, 2462f, 2441f, 2470f, 2480f,
).mapIndexed { index, balance -> index.toFloat() to balance }

@Preview
@Composable
private fun AccountDetailsScreenPreview() {
    AppTheme {
        AccountDetailsContent(
            state = AccountDetailsState.Content(
                accountId = AccountId("preview-acc-1"),
                name = "Everyday",
                formattedBalance = "€2,480.32",
                type = AccountType.BANK,
                formattedIncome = "€3,200.00",
                formattedExpenses = "€1,148.49",
                chart = AccountBalanceChartUi(
                    points = PreviewChartPoints,
                    formattedDelta = "+€412.00",
                    isDeltaNegative = false,
                ),
                transactions = listOf(
                    AccountTransactionUi(
                        id = TransactionId("preview-tx-1"),
                        title = "Lidl — weekly shop",
                        formattedAmount = "−€48.20",
                        isExpense = true,
                        categoryHueSeed = "preview-cat-1",
                    ),
                    AccountTransactionUi(
                        id = TransactionId("preview-tx-2"),
                        title = "Salary",
                        formattedAmount = "€3,200.00",
                        isExpense = false,
                        categoryHueSeed = "preview-cat-2",
                    ),
                    AccountTransactionUi(
                        id = TransactionId("preview-tx-3"),
                        title = "Coffee",
                        formattedAmount = "−€3.80",
                        isExpense = true,
                        categoryHueSeed = "preview-cat-3",
                    ),
                ),
                filter = TransactionFilter.All,
                extraDetails = listOf(
                    AccountExtraDetail(
                        key = AccountExtraDetailKey.IBAN.name,
                        value = "PL61 1090 1014 0000 0712 1981 2874",
                    ),
                    AccountExtraDetail(key = "Broker code", value = "MS-4417"),
                ),
            ),
            onEvent = {},
        )
    }
}
