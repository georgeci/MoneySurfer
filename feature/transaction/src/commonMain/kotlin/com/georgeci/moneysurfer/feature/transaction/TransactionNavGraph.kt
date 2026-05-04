package com.georgeci.moneysurfer.feature.transaction

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationScreen
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsScreen
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountScreen
import com.georgeci.moneysurfer.navigation.AccountPickerResultKey
import com.georgeci.moneysurfer.navigation.CategoryPickerResultKey
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.util.rememberNavigationResult
import io.github.irgaly.navigation3.resultstate.NavigationResultMetadata
import io.github.irgaly.navigation3.resultstate.resultConsumer

val transactionNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.TransactionsByAccount> { key ->
        TransactionsByAccountScreen(
            accountId = key.accountId?.let { AccountId(it) },
            onNavigateBack = { navigator.pop() },
            onNavigateToTransactionCreation = { accountId ->
                navigator.push(Route.TransactionCreation(accountId = accountId?.value))
            },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
            },
        )
    }

    entry<Route.TransactionCreation>(
        metadata = mapOf(
            NavigationResultMetadata.ResultConsumerKey.toString() to
                NavigationResultMetadata.resultConsumer(
                    CategoryPickerResultKey,
                    AccountPickerResultKey,
                ),
        ),
    ) { key ->
        val pickedCategoryId = rememberNavigationResult(CategoryPickerResultKey)
        val pickedAccountId = rememberNavigationResult(AccountPickerResultKey)

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
            onNavigateToAccountChooser = { selectedId ->
                navigator.push(Route.AccountChooser(selectedAccountId = selectedId?.value))
            },
            pickedCategoryId = pickedCategoryId,
            pickedAccountId = pickedAccountId,
        )
    }

    entry<Route.TransactionDetails> { key ->
        TransactionDetailsScreen(
            transactionId = TransactionId(key.transactionId),
            onNavigateBack = { navigator.pop() },
            onNavigateToEdit = { transactionId ->
                navigator.push(Route.TransactionCreation(transactionId = transactionId.value))
            },
        )
    }
}
