package com.georgeci.moneysurfer.feature.account.manage

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.ArchiveAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreAccountUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountsManageViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val accountRepository: AccountRepository,
    private val archiveAccount: ArchiveAccountUseCase,
    private val restoreAccount: RestoreAccountUseCase,
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
            AccountsManageEvent.OnToggleEditMode -> toggleEditMode()
            is AccountsManageEvent.OnAccountClick -> handleAccountClick(event.accountId)
            is AccountsManageEvent.OnArchiveAccountClick -> requestArchive(event.accountId)
            AccountsManageEvent.OnArchiveCancel -> dismissArchive()
            AccountsManageEvent.OnArchiveConfirm -> confirmArchive()
            is AccountsManageEvent.OnRestoreAccountClick -> performRestore(event.accountId)
            AccountsManageEvent.OnUndoArchive -> currentState.lastArchivedId()?.let(::performRestore)
            is AccountsManageEvent.OnRemoveAccountClick -> requestDelete(event.accountId)
            AccountsManageEvent.OnDeleteCancel -> dismissDelete()
            AccountsManageEvent.OnDeleteConfirm -> confirmDelete()
        }
    }

    private fun toggleEditMode() = updateState {
        AccountsManageState.content.isEditing.modify(this) { !it }
    }

    private fun handleAccountClick(accountId: AccountId) {
        val isEditing = (currentState as? AccountsManageState.Content)?.isEditing == true
        val effect = if (isEditing) {
            AccountsManageEffect.NavigateToAccountEdit(accountId)
        } else {
            AccountsManageEffect.NavigateToAccountDetails(accountId)
        }
        postSideEffect(effect)
    }

    private fun requestArchive(accountId: AccountId) {
        val content = currentState as? AccountsManageState.Content ?: return
        val target = content.activeAccounts.firstOrNull { it.id == accountId } ?: return
        updateState {
            AccountsManageState.content.pendingArchive.modify(this) {
                AccountsManagePendingArchive(id = target.id, name = target.name)
            }
        }
    }

    private fun dismissArchive() = updateState {
        AccountsManageState.content.pendingArchive.modify(this) { null }
    }

    private fun confirmArchive() {
        val content = currentState as? AccountsManageState.Content ?: return
        val target = content.pendingArchive ?: return
        updateState { AccountsManageState.content.pendingArchive.modify(this) { null } }
        launch {
            archiveAccount(target.id).fold(
                ifLeft = { postSideEffect(AccountsManageEffect.ShowError) },
                ifRight = { postSideEffect(AccountsManageEffect.ShowArchivedSnackbar(target.id, target.name)) },
            )
        }
    }

    private fun performRestore(accountId: AccountId) {
        launch {
            restoreAccount(accountId).onLeft {
                postSideEffect(AccountsManageEffect.ShowError)
            }
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
                val active = accounts.filterNot { it.archived }.map { it.toUi() }
                val archived = accounts.filter { it.archived }.map { it.toUi() }
                val total = accounts.filterNot { it.archived }.formattedTotal()
                updateState {
                    when (this) {
                        is AccountsManageState.Loading -> AccountsManageState.Content(
                            isEditing = false,
                            activeAccounts = active,
                            archivedAccounts = archived,
                            formattedTotal = total,
                        )
                        is AccountsManageState.Content -> copy(
                            activeAccounts = active,
                            archivedAccounts = archived,
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

    private fun AccountsManageState.lastArchivedId(): AccountId? =
        (this as? AccountsManageState.Content)?.archivedAccounts?.lastOrNull()?.id
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
        val pendingArchive: AccountsManagePendingArchive? = null,
    ) : AccountsManageState {
        companion object
    }

    companion object
}

data class AccountsManagePendingDelete(
    val id: AccountId,
    val name: String,
)

data class AccountsManagePendingArchive(
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
    data object OnArchiveConfirm : AccountsManageEvent
    data object OnArchiveCancel : AccountsManageEvent
    data object OnUndoArchive : AccountsManageEvent
    data class OnRestoreAccountClick(val accountId: AccountId) : AccountsManageEvent
    data class OnRemoveAccountClick(val accountId: AccountId) : AccountsManageEvent
    data object OnDeleteConfirm : AccountsManageEvent
    data object OnDeleteCancel : AccountsManageEvent
}

sealed interface AccountsManageEffect {
    data object NavigateBack : AccountsManageEffect
    data object NavigateToAccountCreation : AccountsManageEffect
    data class NavigateToAccountDetails(val accountId: AccountId) : AccountsManageEffect
    data class NavigateToAccountEdit(val accountId: AccountId) : AccountsManageEffect
    data class ShowArchivedSnackbar(val accountId: AccountId, val name: String) : AccountsManageEffect
    data object ShowError : AccountsManageEffect
}
