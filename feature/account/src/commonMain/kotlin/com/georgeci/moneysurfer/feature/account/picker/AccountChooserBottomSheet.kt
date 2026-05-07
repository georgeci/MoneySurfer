package com.georgeci.moneysurfer.feature.account.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.transaction_creation_account_add_new
import com.georgeci.moneysurfer.feature.account.generated.resources.transaction_creation_account_add_new_subtitle
import com.georgeci.moneysurfer.feature.account.generated.resources.transaction_creation_account_sheet_title
import com.georgeci.moneysurfer.feature.account.generated.resources.transaction_creation_account_summary
import com.georgeci.moneysurfer.feature.account.generated.resources.transaction_creation_account_summary_empty
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.utils.HandleSideEffect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Bottom-sheet entry composable for picking an account. Hosted as a navigation entry; the
 * surrounding [androidx.compose.material3.ModalBottomSheet] is provided by
 * [com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy]. Visual layout lives
 * in [AccountChooserContent] — this composable just maps the ViewModel's state and effects.
 */
@Composable
fun AccountChooserBottomSheet(
    selectedAccountId: AccountId?,
    excludeAccountId: AccountId? = null,
    onDismiss: () -> Unit,
    onNavigateToAccountCreation: () -> Unit,
    onAccountPicked: (AccountId) -> Unit,
    viewModel: AccountChooserViewModel = koinViewModel(
        key = "${selectedAccountId?.value.orEmpty()}|${excludeAccountId?.value.orEmpty()}",
    ) { parametersOf(selectedAccountId, excludeAccountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            AccountChooserEffect.Dismiss -> onDismiss()
            AccountChooserEffect.NavigateToAccountCreation -> onNavigateToAccountCreation()
            is AccountChooserEffect.PublishResult -> onAccountPicked(effect.id)
        }
    }

    val content = state as? AccountChooserState.Content
    val accounts = content?.accounts.orEmpty()
    val totalFormatted = content?.totalFormatted

    val summary = if (accounts.isEmpty() || totalFormatted == null) {
        stringResource(Res.string.transaction_creation_account_summary_empty)
    } else {
        stringResource(
            Res.string.transaction_creation_account_summary,
            accounts.size,
            totalFormatted,
        )
    }

    AccountChooserContent(
        title = stringResource(Res.string.transaction_creation_account_sheet_title),
        summary = summary,
        accounts = accounts.map { account ->
            AccountPickerRow(
                id = account.id,
                name = account.name,
                subtitle = account.currencyCode.value,
                formattedBalance = MoneyFormatter.format(account.balance, account.currencyCode),
                icon = SurferIcons.CreditCard,
            )
        },
        selectedId = state.selectedId,
        addNewLabel = stringResource(Res.string.transaction_creation_account_add_new),
        addNewSubtitle = stringResource(Res.string.transaction_creation_account_add_new_subtitle),
        onSelect = { id -> viewModel.onEvent(AccountChooserEvent.OnAccountSelected(id)) },
        onAddNew = { viewModel.onEvent(AccountChooserEvent.OnAddNewAccountClick) },
        onClose = { viewModel.onEvent(AccountChooserEvent.OnDismiss) },
    )
}
