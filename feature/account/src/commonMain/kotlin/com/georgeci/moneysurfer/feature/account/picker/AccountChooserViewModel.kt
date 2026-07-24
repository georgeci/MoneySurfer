package com.georgeci.moneysurfer.feature.account.picker

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.formattedTotalsByCurrency
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountChooserViewModel(
    initialSelectedId: AccountId?,
    private val excludeAccountId: AccountId?,
    private val getAccounts: GetAccountsUseCase,
) : MviViewModel<AccountChooserState, AccountChooserEvent, AccountChooserEffect>(
    initialState = AccountChooserState.Loading(selectedId = initialSelectedId),
) {

    init {
        observeAccounts()
    }

    override fun onEvent(event: AccountChooserEvent) {
        when (event) {
            is AccountChooserEvent.OnAccountSelected ->
                postSideEffect(AccountChooserEffect.PublishResult(event.id))
            AccountChooserEvent.OnAddNewAccountClick ->
                postSideEffect(AccountChooserEffect.NavigateToAccountCreation)
            AccountChooserEvent.OnTransferInsteadClick ->
                postSideEffect(AccountChooserEffect.RequestTransfer)
            AccountChooserEvent.OnDismiss -> postSideEffect(AccountChooserEffect.Dismiss)
        }
    }

    private fun observeAccounts() {
        launch {
            getAccounts().collect { all ->
                val accounts = if (excludeAccountId != null) {
                    all.filter { it.id != excludeAccountId }
                } else {
                    all
                }
                updateState {
                    AccountChooserState.Content(
                        accounts = accounts,
                        selectedId = selectedId,
                        // One figure per currency — the old single sum added minor units across
                        // currencies and labelled the result with whichever one came first.
                        totalsFormatted = accounts.formattedTotalsByCurrency(),
                    )
                }
            }
        }
    }
}

@optics
sealed interface AccountChooserState {
    val selectedId: AccountId?

    @optics
    data class Loading(override val selectedId: AccountId?) : AccountChooserState {
        companion object
    }

    @optics
    data class Content(
        val accounts: List<Account>,
        override val selectedId: AccountId?,
        /** One formatted total per currency, most-used currency first. Empty when there are none. */
        val totalsFormatted: List<String>,
    ) : AccountChooserState {
        companion object
    }

    companion object
}

sealed interface AccountChooserEvent {
    data class OnAccountSelected(val id: AccountId) : AccountChooserEvent
    data object OnAddNewAccountClick : AccountChooserEvent
    data object OnTransferInsteadClick : AccountChooserEvent
    data object OnDismiss : AccountChooserEvent
}

sealed interface AccountChooserEffect {
    data object NavigateToAccountCreation : AccountChooserEffect
    data object Dismiss : AccountChooserEffect
    data class PublishResult(val id: AccountId) : AccountChooserEffect

    /** The user wants a transfer rather than a single-account entry — hand that back to the host. */
    data object RequestTransfer : AccountChooserEffect
}
