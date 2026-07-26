package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_new
import moneysurfer.feature.transaction.generated.resources.transactions_list_title
import moneysurfer.feature.transaction.generated.resources.transactions_list_untitled
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
internal fun TransactionsByAccountContent(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    val titleFallback = stringResource(Res.string.transactions_list_title)
    val title = state.accountName.ifBlank { titleFallback }
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

            TransactionsList(
                state = state,
                onEvent = onEvent,
                bottomPadding = padding.calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun TransactionsList(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
    bottomPadding: Dp,
) {
    val untitled = stringResource(Res.string.transactions_list_untitled)
    val listState = rememberLazyListState()
    LoadMoreOnScrollToEnd(
        listState = listState,
        enabled = state.canLoadMore,
        onLoadMore = { onEvent(TransactionsByAccountEvent.OnLoadMore) },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
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
