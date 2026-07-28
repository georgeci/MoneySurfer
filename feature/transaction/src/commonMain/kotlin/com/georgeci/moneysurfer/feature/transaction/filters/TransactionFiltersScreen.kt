package com.georgeci.moneysurfer.feature.transaction.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_filters_apply
import moneysurfer.feature.transaction.generated.resources.transaction_filters_cancel
import moneysurfer.feature.transaction.generated.resources.transaction_filters_reset
import moneysurfer.feature.transaction.generated.resources.transaction_filters_results
import moneysurfer.feature.transaction.generated.resources.transaction_filters_results_capped
import moneysurfer.feature.transaction.generated.resources.transaction_filters_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Apply carries a counted label, so it needs more room than the bare "Cancel" beside it. */
private const val APPLY_WEIGHT = 1.4f

object TransactionFiltersTestTags {
    const val Root = "transactionFilters:root"
    const val Apply = "transactionFilters:apply"
    const val Cancel = "transactionFilters:cancel"

    /**
     * Transaction-type option, suffixed with the lowercased
     * [com.georgeci.moneysurfer.domain.model.TransactionTypeFilter]. The Apply label carries a
     * live result count, so it can never be a stable text selector.
     */
    const val TypePrefix = "transactionFilters:type:"
}

@Composable
fun TransactionFiltersScreen(
    accountId: AccountId?,
    anchorEpochDay: Long?,
    onNavigateBack: () -> Unit,
    // Key on the anchor as well: reopening the screen on a different paged period must build a
    // fresh ViewModel so its result count reflects that period, not the one it first opened on.
    viewModel: TransactionFiltersViewModel = koinViewModel(
        key = "${accountId?.value.orEmpty()}:$anchorEpochDay",
    ) { parametersOf(accountId, anchorEpochDay) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionFiltersEffect.NavigateBack -> onNavigateBack()
        }
    }

    TransactionFiltersContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun TransactionFiltersContent(
    state: TransactionFiltersState,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(TransactionFiltersTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.transaction_filters_title),
                onBack = { onEvent(TransactionFiltersEvent.OnCancelClick) },
                actions = {
                    TextButton(
                        onClick = { onEvent(TransactionFiltersEvent.OnResetClick) },
                        enabled = state.canReset,
                    ) {
                        Text(stringResource(Res.string.transaction_filters_reset))
                    }
                },
            )
        },
        bottomBar = { FiltersFooter(state = state, onEvent = onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            DateRangeSection(dateRange = state.draft.dateRange, onEvent = onEvent)
            TypeSection(type = state.draft.type, onEvent = onEvent)
            if (state.showAccounts) {
                AccountsSection(
                    accounts = state.accounts,
                    selected = state.draft.accountIds,
                    onEvent = onEvent,
                )
            }
            CategoriesSection(
                categories = state.categories,
                selected = state.draft.categoryIds,
                onEvent = onEvent,
            )
            AmountSection(
                minAmount = state.draft.minAmount,
                maxAmount = state.draft.maxAmount,
                onEvent = onEvent,
            )
            OtherSection(filters = state.draft, onEvent = onEvent)
            SortSection(sort = state.draft.sort, onEvent = onEvent)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Sticky footer. `Apply` carries the live result count — a real count over the same windowed
 * query the list runs, so the number the user commits to is the number they land on.
 */
@Composable
private fun FiltersFooter(
    state: TransactionFiltersState,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    val results = if (state.resultCountCapped) {
        stringResource(Res.string.transaction_filters_results_capped, state.resultCount)
    } else {
        stringResource(Res.string.transaction_filters_results, state.resultCount)
    }
    Surface(color = AppTheme.materialColors.surface) {
        Column {
            HorizontalDivider(color = AppTheme.materialColors.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SurferButton(
                    text = stringResource(Res.string.transaction_filters_cancel),
                    onClick = { onEvent(TransactionFiltersEvent.OnCancelClick) },
                    style = SurferButtonStyle.Outlined,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TransactionFiltersTestTags.Cancel),
                )
                SurferButton(
                    text = stringResource(Res.string.transaction_filters_apply, results),
                    onClick = { onEvent(TransactionFiltersEvent.OnApplyClick) },
                    modifier = Modifier
                        .weight(APPLY_WEIGHT)
                        .testTag(TransactionFiltersTestTags.Apply),
                )
            }
        }
    }
}

@Preview
@Composable
private fun TransactionFiltersPreview() {
    AppTheme {
        TransactionFiltersContent(
            state = TransactionFiltersState(
                draft = TransactionFilters.Empty,
                showAccounts = true,
                resultCount = 47,
            ),
            onEvent = {},
        )
    }
}
