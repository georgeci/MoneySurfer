package com.georgeci.moneysurfer.feature.account

import androidx.compose.material3.ExperimentalMaterial3Api
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.feature.account.creation.AccountCreationScreen
import com.georgeci.moneysurfer.feature.account.details.AccountDetailsScreen
import com.georgeci.moneysurfer.feature.account.manage.AccountsManageScreen
import com.georgeci.moneysurfer.feature.account.picker.AccountChooserBottomSheet
import com.georgeci.moneysurfer.navigation.AccountPickerResultKey
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route
import io.github.irgaly.navigation3.resultstate.LocalNavigationResultProducer
import io.github.irgaly.navigation3.resultstate.setResult

@OptIn(ExperimentalMaterial3Api::class)
val accountNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.AccountCreation> { key ->
        AccountCreationScreen(
            onNavigateBack = { navigator.pop() },
            accountId = key.accountId?.let { AccountId(it) },
        )
    }

    entry<Route.AccountsManage> {
        AccountsManageScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToAccountCreation = { navigator.push(Route.AccountCreation()) },
            onNavigateToAccountEdit = { accountId ->
                navigator.push(Route.AccountCreation(accountId = accountId.value))
            },
            onNavigateToAccountDetails = { accountId ->
                navigator.push(Route.AccountDetails(accountId = accountId.value))
            },
        )
    }

    entry<Route.AccountChooser>(
        metadata = BottomSheetSceneStrategy.bottomSheet(),
    ) { key ->
        val resultProducer = LocalNavigationResultProducer.current
        AccountChooserBottomSheet(
            selectedAccountId = key.selectedAccountId?.let { AccountId(it) },
            onDismiss = { navigator.pop() },
            onNavigateToAccountCreation = {
                navigator.replaceTop(Route.AccountCreation())
            },
            onAccountPicked = { picked ->
                resultProducer.setResult(AccountPickerResultKey, picked)
                navigator.pop()
            },
        )
    }

    entry<Route.AccountDetails> { key ->
        AccountDetailsScreen(
            accountId = AccountId(key.accountId),
            onNavigateBack = { navigator.pop() },
            onNavigateToTransactionCreation = { accountId ->
                navigator.push(Route.TransactionCreation(accountId = accountId.value))
            },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
            },
            onNavigateToAccountEdit = { accountId ->
                navigator.push(Route.AccountCreation(accountId = accountId.value))
            },
            onNavigateToTransactionsList = { accountId ->
                navigator.push(Route.TransactionsByAccount(accountId = accountId.value))
            },
        )
    }
}
