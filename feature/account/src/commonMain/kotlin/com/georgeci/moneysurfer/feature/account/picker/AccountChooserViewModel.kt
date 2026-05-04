package com.georgeci.moneysurfer.feature.account.picker

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountChooserViewModel(
    initialSelectedId: AccountId?,
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
            AccountChooserEvent.OnDismiss -> postSideEffect(AccountChooserEffect.Dismiss)
        }
    }

    private fun observeAccounts() {
        launch {
            getAccounts().collect { accounts ->
                val total = accounts.fold(0L) { acc, a -> acc + a.balance.minor }
                val currency = accounts.firstOrNull()?.currencyCode
                val totalFormatted = currency?.let { MoneyFormatter.format(Money(total), it) }
                updateState {
                    AccountChooserState.Content(
                        accounts = accounts,
                        selectedId = selectedId,
                        totalFormatted = totalFormatted,
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
        val totalFormatted: String?,
    ) : AccountChooserState {
        companion object
    }

    companion object
}

sealed interface AccountChooserEvent {
    data class OnAccountSelected(val id: AccountId) : AccountChooserEvent
    data object OnAddNewAccountClick : AccountChooserEvent
    data object OnDismiss : AccountChooserEvent
}

sealed interface AccountChooserEffect {
    data object NavigateToAccountCreation : AccountChooserEffect
    data object Dismiss : AccountChooserEffect
    data class PublishResult(val id: AccountId) : AccountChooserEffect
}
