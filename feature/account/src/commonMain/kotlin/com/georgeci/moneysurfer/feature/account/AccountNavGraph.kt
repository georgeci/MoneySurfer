package com.georgeci.moneysurfer.feature.account

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.creation.AccountCreationScreen
import com.georgeci.moneysurfer.feature.account.details.AccountDetailsScreen
import com.georgeci.moneysurfer.feature.account.manage.AccountsManageScreen
import com.georgeci.moneysurfer.feature.account.picker.AccountChooserBottomSheet
import com.georgeci.moneysurfer.navigation.AccountPickerResultKey
import com.georgeci.moneysurfer.navigation.AccountPickerTransferResultKey
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize
import io.github.irgaly.navigation3.resultstate.LocalNavigationResultProducer
import io.github.irgaly.navigation3.resultstate.setResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
val accountNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.AccountCreation>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        AccountCreationScreen(
            onNavigateBack = { navigator.pop() },
            // First-launch step has nothing below it on the stack — reset instead of popping.
            onNavigateToDashboard = { navigator.resetTo(Route.Dashboard) },
            accountId = key.accountId?.let { AccountId(it) },
            firstRun = key.firstRun,
            initialType = key.accountType?.toAccountType() ?: DefaultAccountType,
        )
    }

    entry<Route.AccountsManage>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) {
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
            excludeAccountId = key.excludeAccountId?.let { AccountId(it) },
            showTransferShortcut = key.showTransferShortcut,
            onDismiss = { navigator.pop() },
            onNavigateToAccountCreation = {
                navigator.replaceTop(Route.AccountCreation())
            },
            onAccountPicked = { picked ->
                resultProducer.setResult(AccountPickerResultKey, picked)
                navigator.pop()
            },
            onTransferRequested = {
                resultProducer.setResult(AccountPickerTransferResultKey, true)
                navigator.pop()
            },
        )
    }

    entry<Route.AccountDetails>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
        // The panel route is only pushed on a window that can actually lay it out as a third
        // column. Below that it would be presented as the ordinary full-screen form anyway, and
        // pushing it would leave an extra-pane entry buried in the back stack: anything stacked on
        // top of it (the form's own "create category", say) makes the pane host decline the whole
        // scene, collapsing the section from two panes to one. Pushing the plain creation route
        // keeps narrow and Expanded windows exactly where they were.
        val inlinePanel = currentSurferWindowSize() >= SurferWindowSize.Large
        AccountDetailsScreen(
            accountId = AccountId(key.accountId),
            onNavigateBack = { navigator.pop() },
            onNavigateToTransactionCreation = { accountId ->
                navigator.push(
                    if (inlinePanel) {
                        Route.AccountTransactionCreation(accountId = accountId.value)
                    } else {
                        Route.TransactionCreation(accountId = accountId.value)
                    },
                )
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

/** Type the creation screen opens on when the caller has no opinion — the mockup's "Bank". */
private val DefaultAccountType = AccountType.BANK

/** Unknown names can only come from a hand-edited saved stack — fall back instead of crashing. */
private fun String.toAccountType(): AccountType? =
    AccountType.entries.firstOrNull { it.name == this }
