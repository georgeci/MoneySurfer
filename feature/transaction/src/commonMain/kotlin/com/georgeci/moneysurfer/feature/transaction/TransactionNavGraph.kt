package com.georgeci.moneysurfer.feature.transaction

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationScreen
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsScreen
import com.georgeci.moneysurfer.feature.transaction.filters.TransactionFiltersScreen
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountScreen
import com.georgeci.moneysurfer.navigation.AccountPickerResultKey
import com.georgeci.moneysurfer.navigation.AccountPickerTransferResultKey
import com.georgeci.moneysurfer.navigation.CategoryPickerResultKey
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.util.rememberNavigationResult
import io.github.irgaly.navigation3.resultstate.NavigationResultMetadata
import io.github.irgaly.navigation3.resultstate.resultConsumer

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val transactionNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.TransactionsByAccount>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
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
            onNavigateToFilters = { accountId ->
                navigator.push(Route.TransactionFilters(accountId = accountId?.value))
            },
        )
    }

    entry<Route.TransactionFilters> { key ->
        TransactionFiltersScreen(
            accountId = key.accountId?.let { AccountId(it) },
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.TransactionCreation>(
        metadata = mapOf(
            NavigationResultMetadata.ResultConsumerKey.toString() to
                NavigationResultMetadata.resultConsumer(
                    CategoryPickerResultKey,
                    AccountPickerResultKey,
                    AccountPickerTransferResultKey,
                ),
        ) + ListDetailSceneStrategy.detailPane(),
    ) { key ->
        val pickedCategoryId = rememberNavigationResult(CategoryPickerResultKey)
        val pickedAccountId = rememberNavigationResult(AccountPickerResultKey)
        val transferRequested = rememberNavigationResult(AccountPickerTransferResultKey)

        TransactionCreationScreen(
            transactionId = key.transactionId?.let { TransactionId(it) },
            accountId = key.accountId?.let { AccountId(it) },
            onNavigateBack = { navigator.pop() },
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

    entry<Route.TransactionDetails>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { key ->
        TransactionDetailsScreen(
            transactionId = TransactionId(key.transactionId),
            onNavigateBack = { navigator.pop() },
            onNavigateToEdit = { transactionId ->
                navigator.push(Route.TransactionCreation(transactionId = transactionId.value))
            },
        )
    }
}
