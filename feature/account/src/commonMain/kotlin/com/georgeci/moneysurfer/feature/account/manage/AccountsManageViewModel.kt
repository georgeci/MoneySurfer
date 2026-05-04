package com.georgeci.moneysurfer.feature.account.manage

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountsManageViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val accountRepository: AccountRepository,
) : MviViewModel<AccountsManageState, AccountsManageEvent, AccountsManageEffect>(
    initialState = AccountsManageState.Loading,
) {

    init {
        observeAccounts()
    }

    override fun onEvent(event: AccountsManageEvent) {
        when (event) {
            AccountsManageEvent.OnBackClick -> postSideEffect(AccountsManageEffect.NavigateBack)
            AccountsManageEvent.OnAddAccountClick -> postSideEffect(AccountsManageEffect.NavigateToAccountCreation)
            AccountsManageEvent.OnToggleEditMode ->
                updateState {
                    AccountsManageState.content.isEditing.modify(this) { !it }
                }
            is AccountsManageEvent.OnAccountClick -> {
                val isEditing = (currentState as? AccountsManageState.Content)?.isEditing == true
                if (isEditing) {
                    postSideEffect(AccountsManageEffect.NavigateToAccountEdit(event.accountId))
                } else {
                    postSideEffect(AccountsManageEffect.NavigateToAccountDetails(event.accountId))
                }
            }
            is AccountsManageEvent.OnArchiveAccountClick -> Unit // TODO: wire when Account.archived lands
            is AccountsManageEvent.OnRemoveAccountClick -> requestDelete(event.accountId)
            AccountsManageEvent.OnDeleteCancel -> dismissDelete()
            AccountsManageEvent.OnDeleteConfirm -> confirmDelete()
            is AccountsManageEvent.OnRestoreAccountClick -> Unit // TODO
        }
    }

    private fun requestDelete(accountId: AccountId) {
        val content = currentState as? AccountsManageState.Content ?: return
        val target = content.activeAccounts.firstOrNull { it.id == accountId }
            ?: content.archivedAccounts.firstOrNull { it.id == accountId }
            ?: return
        updateState {
            AccountsManageState.content.pendingDelete.modify(this) {
                AccountsManagePendingDelete(id = target.id, name = target.name)
            }
        }
    }

    private fun dismissDelete() = updateState {
        AccountsManageState.content.pendingDelete.modify(this) { null }
    }

    private fun confirmDelete() {
        val content = currentState as? AccountsManageState.Content ?: return
        val target = content.pendingDelete ?: return
        updateState { AccountsManageState.content.pendingDelete.modify(this) { null } }
        launch { accountRepository.delete(target.id) }
    }

    private fun observeAccounts() {
        launch {
            getAccounts().collect { accounts ->
                val active = accounts.map { it.toUi() }
                val total = accounts.formattedTotal()
                updateState {
                    when (this) {
                        is AccountsManageState.Loading -> AccountsManageState.Content(
                            isEditing = false,
                            activeAccounts = active,
                            archivedAccounts = emptyList(),
                            formattedTotal = total,
                        )
                        is AccountsManageState.Content -> copy(
                            activeAccounts = active,
                            formattedTotal = total,
                        )
                    }
                }
            }
        }
    }

    private fun List<Account>.formattedTotal(): String? {
        val currency = firstOrNull()?.currencyCode ?: return null
        val total = filter { it.currencyCode == currency }
            .fold(Money.zero()) { acc, account -> acc + account.balance }
        return MoneyFormatter.format(total, currency)
    }

    private fun Account.toUi() = AccountManageUi(
        id = id,
        name = name,
        type = type,
        formattedBalance = MoneyFormatter.format(balance, currencyCode),
        currency = currencyCode.value,
    )
}

@optics
sealed interface AccountsManageState {
    data object Loading : AccountsManageState

    @optics
    data class Content(
        val isEditing: Boolean,
        val activeAccounts: List<AccountManageUi>,
        val archivedAccounts: List<AccountManageUi>,
        val formattedTotal: String?,
        val pendingDelete: AccountsManagePendingDelete? = null,
    ) : AccountsManageState {
        companion object
    }

    companion object
}

data class AccountsManagePendingDelete(
    val id: AccountId,
    val name: String,
)

data class AccountManageUi(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val formattedBalance: String,
    val currency: String,
    val archivedLabel: String? = null,
)

sealed interface AccountsManageEvent {
    data object OnBackClick : AccountsManageEvent
    data object OnAddAccountClick : AccountsManageEvent
    data object OnToggleEditMode : AccountsManageEvent
    data class OnAccountClick(val accountId: AccountId) : AccountsManageEvent
    data class OnArchiveAccountClick(val accountId: AccountId) : AccountsManageEvent
    data class OnRemoveAccountClick(val accountId: AccountId) : AccountsManageEvent
    data object OnDeleteConfirm : AccountsManageEvent
    data object OnDeleteCancel : AccountsManageEvent
    data class OnRestoreAccountClick(val accountId: AccountId) : AccountsManageEvent
}

sealed interface AccountsManageEffect {
    data object NavigateBack : AccountsManageEffect
    data object NavigateToAccountCreation : AccountsManageEffect
    data class NavigateToAccountDetails(val accountId: AccountId) : AccountsManageEffect
    data class NavigateToAccountEdit(val accountId: AccountId) : AccountsManageEffect
}
