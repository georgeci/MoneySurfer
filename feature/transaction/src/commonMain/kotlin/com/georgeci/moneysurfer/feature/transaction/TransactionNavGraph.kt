package com.georgeci.moneysurfer.feature.transaction

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationScreen
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsScreen
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersScreen
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountScreen
import com.georgeci.moneysurfer.navigation.AccountPickerResultKey
import com.georgeci.moneysurfer.navigation.AccountPickerTransferResultKey
import com.georgeci.moneysurfer.navigation.AppNavigator
import com.georgeci.moneysurfer.navigation.CategoryPickerResultKey
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy
import com.georgeci.moneysurfer.navigation.util.rememberNavigationResult
import io.github.irgaly.navigation3.resultstate.NavigationResultMetadata
import io.github.irgaly.navigation3.resultstate.resultConsumer

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val transactionNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.TransactionsByAccount>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) { key ->
        TransactionsByAccountScreen(
            accountId = key.accountId?.let { AccountId(it) },
            onNavigateBack = { navigator.pop() },
            onNavigateToTransactionCreation = { accountId ->
                navigator.push(Route.TransactionCreation(accountId = accountId?.value))
            },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
            },
            onNavigateToFilters = { accountId, anchorEpochDay ->
                navigator.push(
                    Route.TransactionFilters(
                        accountId = accountId?.value,
                        anchorEpochDay = anchorEpochDay,
                    ),
                )
            },
        )
    }

    entry<Route.TransactionFilters> { key ->
        TransactionFiltersScreen(
            accountId = key.accountId?.let { AccountId(it) },
            anchorEpochDay = key.anchorEpochDay,
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.TransactionCreation>(
        metadata = TransactionCreationResultMetadata + SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        TransactionCreationEntry(
            navigator = navigator,
            transactionId = key.transactionId,
            accountId = key.accountId,
            duplicate = key.duplicate,
            transfer = key.transfer,
        )
    }

    // The same form, presented as the design's inline add panel beside an account (issue #391).
    // Registered here rather than in the account graph so both presentations share one entry body,
    // and with it one ViewModel, one set of picker results and one back behaviour.
    entry<Route.AccountTransactionCreation>(
        metadata = TransactionCreationResultMetadata + SurferPaneSceneStrategy.extraPane(),
    ) { key ->
        TransactionCreationEntry(navigator = navigator, accountId = key.accountId)
    }

    entry<Route.TransactionDetails>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        TransactionDetailsScreen(
            transactionId = TransactionId(key.transactionId),
            onNavigateBack = { navigator.pop() },
            onNavigateToEdit = { transactionId ->
                navigator.push(Route.TransactionCreation(transactionId = transactionId.value))
            },
            onNavigateToDuplicate = { transactionId ->
                navigator.push(
                    Route.TransactionCreation(transactionId = transactionId.value, duplicate = true),
                )
            },
        )
    }
}

/** The picker results the creation form consumes, whichever route presents it. */
private val TransactionCreationResultMetadata: Map<String, Any> = mapOf(
    NavigationResultMetadata.ResultConsumerKey.toString() to
        NavigationResultMetadata.resultConsumer(
            CategoryPickerResultKey,
            AccountPickerResultKey,
            AccountPickerTransferResultKey,
        ),
)

/**
 * The body shared by [Route.TransactionCreation] and [Route.AccountTransactionCreation]: one
 * screen, one ViewModel, one set of navigation callbacks. The two routes differ only in the pane
 * the host puts them in.
 */
@Composable
private fun TransactionCreationEntry(
    navigator: AppNavigator,
    transactionId: String? = null,
    accountId: String? = null,
    duplicate: Boolean = false,
    transfer: Boolean = false,
) {
    val pickedCategoryId = rememberNavigationResult(CategoryPickerResultKey)
    val pickedAccountId = rememberNavigationResult(AccountPickerResultKey)
    val transferRequested = rememberNavigationResult(AccountPickerTransferResultKey)

    TransactionCreationScreen(
        transactionId = transactionId?.let { TransactionId(it) },
        accountId = accountId?.let { AccountId(it) },
        duplicate = duplicate,
        transfer = transfer,
        onNavigateBack = { navigator.pop() },
        // Edit is only ever pushed from the details of the row being edited, and that row is
        // now gone — returning to it would show a screen for a deleted transaction.
        onNavigateBackAfterDelete = { navigator.pop(count = 2) },
        onNavigateToCategoryChooser = { selectedId, filterType ->
            navigator.push(
                Route.CategoryChooser(
                    selectedCategoryId = selectedId?.value,
                    filterType = filterType.name,
                ),
            )
        },
        onNavigateToCategoryCreation = { navigator.push(Route.CategoryCreation()) },
        onNavigateToAccountChooser = { selectedId, excludeId, showTransferShortcut ->
            navigator.push(
                Route.AccountChooser(
                    selectedAccountId = selectedId?.value,
                    excludeAccountId = excludeId?.value,
                    showTransferShortcut = showTransferShortcut,
                ),
            )
        },
        pickedCategoryId = pickedCategoryId,
        pickedAccountId = pickedAccountId,
        transferRequested = transferRequested,
    )
}
