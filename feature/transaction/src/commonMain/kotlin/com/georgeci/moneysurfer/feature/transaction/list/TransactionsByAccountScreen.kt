package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodArrow
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodPager
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_full
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_today
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_yesterday
import moneysurfer.feature.transaction.generated.resources.transactions_list_months
import moneysurfer.feature.transaction.generated.resources.transactions_list_months_genitive
import moneysurfer.feature.transaction.generated.resources.transactions_list_months_short
import moneysurfer.feature.transaction.generated.resources.transactions_list_new
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_all_time
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_all_time_sub
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_all_time
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_month
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_mode_week
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_next
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_previous
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_range
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_range_cross_month
import moneysurfer.feature.transaction.generated.resources.transactions_list_period_week_sub
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_expenses
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_income
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_net
import moneysurfer.feature.transaction.generated.resources.transactions_list_title
import moneysurfer.feature.transaction.generated.resources.transactions_list_untitled
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stable selectors for the transactions list screen — see docs/testing/testing-strategy.md.
 *
 * The `filter:*` tags name the chips of the rail, not filter *values*: every chip is a shortcut
 * that opens the filters screen rather than a segment that toggles a value on this screen.
 */
object TransactionsListTestTags {
    const val Root = "transactionsList:root"
    const val FilterDate = "transactionsList:filter:date"
    const val FilterType = "transactionsList:filter:type"
    const val FilterAccount = "transactionsList:filter:account"
    const val FilterCategory = "transactionsList:filter:category"
    const val FilterSort = "transactionsList:filter:sort"
}

@Composable
fun TransactionsByAccountScreen(
    accountId: AccountId?,
    onNavigateBack: () -> Unit,
    onNavigateToTransactionCreation: (AccountId?) -> Unit,
    onNavigateToTransactionDetails: (TransactionId) -> Unit,
    onNavigateToFilters: (AccountId?, Long) -> Unit,
    viewModel: TransactionsByAccountViewModel = koinViewModel(
        key = accountId?.value.orEmpty(),
    ) { parametersOf(accountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionsByAccountEffect.NavigateBack -> onNavigateBack()
            is TransactionsByAccountEffect.NavigateToTransactionCreation ->
                onNavigateToTransactionCreation(effect.accountId)
            is TransactionsByAccountEffect.NavigateToTransactionDetails ->
                onNavigateToTransactionDetails(effect.transactionId)
            is TransactionsByAccountEffect.NavigateToFilters ->
                onNavigateToFilters(effect.accountId, effect.anchorEpochDay)
        }
    }

    when (val current = state) {
        is TransactionsByAccountState.Loading -> TransactionsByAccountLoading(
            onEvent = viewModel::onEvent,
        )
        is TransactionsByAccountState.Content -> TransactionsByAccountContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun TransactionsByAccountLoading(onEvent: (TransactionsByAccountEvent) -> Unit) {
    val titleFallback = stringResource(Res.string.transactions_list_title)
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = titleFallback,
                onBack = { onEvent(TransactionsByAccountEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun TransactionsByAccountContent(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    val titleFallback = stringResource(Res.string.transactions_list_title)
    val title = state.accountName.ifBlank { titleFallback }
    val untitled = stringResource(Res.string.transactions_list_untitled)
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(TransactionsListTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = title,
                onBack = { onEvent(TransactionsByAccountEvent.OnBackClick) },
            )
        },
        floatingActionButton = {
            SurferAddFab(
                label = stringResource(Res.string.transactions_list_new),
                onClick = { onEvent(TransactionsByAccountEvent.OnAddTransactionClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            SearchRow(
                query = state.query,
                activeFilterCount = state.activeFilterCount,
                onEvent = onEvent,
            )

            Spacer(Modifier.height(8.dp))

            // Only while the pager still owns the date window — an explicit range from the filter
            // screen takes it over, and two controls over one window is exactly the trap the
            // filters were supposed to avoid.
            if (state.showPeriodPager) {
                PeriodPager(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SummaryStrip(
                summary = state.summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FilterChipRail(
                chips = state.filters,
                onOpenFilters = { onEvent(TransactionsByAccountEvent.OnOpenFiltersClick) },
            )

            Spacer(Modifier.height(4.dp))

            if (state.isEmpty) {
                EmptyState(state = state, onEvent = onEvent)
                return@Scaffold
            }

            val listState = rememberLazyListState()
            LoadMoreOnScrollToEnd(
                listState = listState,
                enabled = state.canLoadMore,
                onLoadMore = { onEvent(TransactionsByAccountEvent.OnLoadMore) },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                state.groups.forEach { group ->
                    item(key = "h-${group.date}") {
                        DateHeader(group = group)
                    }
                    group.transactions.forEach { row ->
                        item(key = "t-${row.id.value}") {
                            TransactionRow(
                                row = row,
                                showAccount = state.showAccountOnRows,
                                untitled = untitled,
                                onClick = { onEvent(TransactionsByAccountEvent.OnTransactionClick(row.id)) },
                                onDelete = { onEvent(TransactionsByAccountEvent.OnDeleteTransaction(row.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The design's `PeriodPager`, plus a menu on the label: the design never drew a mode switcher, and
 * the pill is the only place on the screen where the period is already the subject.
 *
 * In all-time mode the arrows are disabled rather than the pager hidden (the design's
 * `All time · arrows disabled` variant) — hiding it would strand the user with no way back.
 */
@Composable
private fun PeriodPager(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SurferPeriodPager(
            label = state.period.label(),
            sublabel = state.period.sublabel(),
            previous = SurferPeriodArrow(
                onClick = { onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick) },
                enabled = state.canGoToPreviousPeriod,
                contentDescription = stringResource(Res.string.transactions_list_period_previous),
            ),
            next = SurferPeriodArrow(
                onClick = { onEvent(TransactionsByAccountEvent.OnNextPeriodClick) },
                enabled = state.canGoToNextPeriod,
                contentDescription = stringResource(Res.string.transactions_list_period_next),
            ),
            onLabelClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            TransactionPeriodMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        menuExpanded = false
                        onEvent(TransactionsByAccountEvent.OnPeriodModeChanged(mode))
                    },
                )
            }
        }
    }
}

@Composable
private fun TransactionPeriodMode.label(): String = stringResource(
    when (this) {
        TransactionPeriodMode.Month -> Res.string.transactions_list_period_mode_month
        TransactionPeriodMode.Week -> Res.string.transactions_list_period_mode_week
        TransactionPeriodMode.AllTime -> Res.string.transactions_list_period_mode_all_time
    },
)

@Composable
private fun TransactionPeriodUi.label(): String = when (this) {
    is TransactionPeriodUi.Month -> monthNames()[monthNumber - 1]
    is TransactionPeriodUi.Week -> weekRangeLabel(from, to)
    TransactionPeriodUi.AllTime -> stringResource(Res.string.transactions_list_period_all_time)
}

@Composable
private fun TransactionPeriodUi.sublabel(): String = when (this) {
    is TransactionPeriodUi.Month -> year.toString()
    is TransactionPeriodUi.Week ->
        stringResource(Res.string.transactions_list_period_week_sub, weekNumber, weekYear)
    TransactionPeriodUi.AllTime -> stringResource(Res.string.transactions_list_period_all_time_sub)
}

/** `Mar 25 – 31`, or `Mar 30 – Apr 5` when the week straddles two months. */
@Composable
private fun weekRangeLabel(from: LocalDate, to: LocalDate): String {
    val shortMonths = stringArrayResource(Res.array.transactions_list_months_short)
    val fromMonth = shortMonths[from.month.number - 1]
    return if (from.month == to.month) {
        stringResource(Res.string.transactions_list_period_week_range, fromMonth, from.day, to.day)
    } else {
        stringResource(
            Res.string.transactions_list_period_week_range_cross_month,
            fromMonth,
            from.day,
            shortMonths[to.month.number - 1],
            to.day,
        )
    }
}

/** Standalone month names — the pager label is a sentence of its own. */
@Composable
private fun monthNames(): List<String> = stringArrayResource(Res.array.transactions_list_months)

@Composable
private fun TransactionDateUi.label(): String = when (this) {
    TransactionDateUi.Today -> stringResource(Res.string.transactions_list_date_today)
    TransactionDateUi.Yesterday -> stringResource(Res.string.transactions_list_date_yesterday)
    // Genitive, not the pager's nominative: Russian reads "25 марта", never "25 Март".
    is TransactionDateUi.Exact -> stringResource(
        Res.string.transactions_list_date_full,
        date.day,
        stringArrayResource(Res.array.transactions_list_months_genitive)[date.month.number - 1],
        date.year,
    )
}

/**
 * Asks for the next page once the list is within [LOAD_MORE_THRESHOLD] rows of the end, so the
 * fetch overlaps the remaining scroll instead of stalling at the bottom.
 */
@Composable
private fun LoadMoreOnScrollToEnd(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(enabled) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            // Guard the pre-measurement frame: before the list is laid out, totalItemsCount is 0
            // and there are no visible items, which would otherwise satisfy `0 >= 0 - threshold`
            // and fire a page load with no scroll behind it.
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            enabled && lastVisible != null &&
                lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}

private const val LOAD_MORE_THRESHOLD = 10

@Composable
private fun SummaryStrip(
    summary: TransactionSummaryUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_income),
            value = summary.incomeFormatted,
            valueColor = AppTheme.semanticColors.income,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_expenses),
            value = summary.expenseFormatted,
            valueColor = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_net),
            value = summary.netFormatted,
            valueColor = if (summary.netPositive) {
                AppTheme.semanticColors.income
            } else {
                AppTheme.materialColors.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = AppTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(28.dp)
            .background(AppTheme.materialColors.outlineVariant),
    )
}

@Composable
private fun DateHeader(group: TransactionGroupUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = group.dateLabel.label(),
            style = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = group.netFormatted,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun TransactionsByAccountPreview() {
    AppTheme {
        TransactionsByAccountContent(
            // All-accounts variant: the one where a row has to say which account it belongs to.
            state = TransactionsByAccountState.Content(
                accountId = null,
                accountName = "",
                groups = listOf(
                    TransactionGroupUi(
                        date = LocalDate(2025, 3, 26),
                        dateLabel = TransactionDateUi.Today,
                        netFormatted = "−€72.70",
                        netPositive = false,
                        transactions = listOf(
                            TransactionRowUi(
                                id = TransactionId("preview-tx-1"),
                                title = "Lidl — weekly shop",
                                subtitle = "Groceries",
                                formattedAmount = "−€48.20",
                                isExpense = true,
                                categoryHueSeed = "preview-cat-1",
                                accountName = "Everyday",
                            ),
                            TransactionRowUi(
                                id = TransactionId("preview-tx-2"),
                                title = "Ramen with J.",
                                subtitle = "Dining",
                                formattedAmount = "−€24.50",
                                isExpense = true,
                                categoryHueSeed = "preview-cat-2",
                                accountName = "Everyday",
                            ),
                            TransactionRowUi(
                                id = TransactionId("preview-tx-4"),
                                title = "Rainy day top-up",
                                subtitle = "Transfer",
                                formattedAmount = "€200.00",
                                isExpense = true,
                                categoryHueSeed = "",
                                accountName = "Savings",
                                isTransfer = true,
                            ),
                        ),
                    ),
                    TransactionGroupUi(
                        date = LocalDate(2025, 3, 25),
                        dateLabel = TransactionDateUi.Yesterday,
                        netFormatted = "+€3,200.00",
                        netPositive = true,
                        transactions = listOf(
                            TransactionRowUi(
                                id = TransactionId("preview-tx-3"),
                                title = "March payroll",
                                subtitle = "Salary",
                                formattedAmount = "+€3,200.00",
                                isExpense = false,
                                categoryHueSeed = "preview-cat-3",
                                accountName = "Everyday",
                            ),
                        ),
                    ),
                ),
                showAccountOnRows = true,
                summary = TransactionSummaryUi(
                    incomeFormatted = "+€3,200.00",
                    expenseFormatted = "−€72.70",
                    netFormatted = "+€3,127.30",
                    netPositive = true,
                ),
                query = "",
                filters = previewChips(),
                activeFilterCount = 0,
                isFiltered = false,
                periodMode = TransactionPeriodMode.Month,
                period = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = false,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionsByAccountEmptyPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Savings",
                groups = emptyList(),
                showAccountOnRows = false,
                summary = previewEmptySummary(),
                query = "",
                filters = previewChips(),
                activeFilterCount = 0,
                isFiltered = false,
                periodMode = TransactionPeriodMode.Week,
                period = TransactionPeriodUi.Week(
                    from = LocalDate(2025, 3, 25),
                    to = LocalDate(2025, 3, 31),
                    weekNumber = 13,
                    weekYear = 2025,
                ),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = true,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}

/** The other empty state: rows exist, the filters are hiding them, so the CTA is the way back. */
@Preview
@Composable
private fun TransactionsByAccountEmptyFilteredPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Savings",
                groups = emptyList(),
                showAccountOnRows = false,
                summary = previewEmptySummary(),
                query = "",
                filters = previewChips().copy(type = TransactionTypeFilter.Transfer),
                activeFilterCount = 1,
                isFiltered = true,
                periodMode = TransactionPeriodMode.Month,
                period = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
                showPeriodPager = true,
                canGoToPreviousPeriod = true,
                canGoToNextPeriod = false,
                canLoadMore = false,
            ),
            onEvent = {},
        )
    }
}
