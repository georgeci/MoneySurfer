package com.georgeci.moneysurfer.feature.account.manage

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.formattedTotalsByCurrency
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.usecase.ArchiveAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.ReorderAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreAccountUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_action_failed
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_undo
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_deleted_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountsManageViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val archiveAccount: ArchiveAccountUseCase,
    private val restoreAccount: RestoreAccountUseCase,
    private val reorderAccounts: ReorderAccountsUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<AccountsManageState, AccountsManageEvent, AccountsManageEffect>(
    initialState = AccountsManageState.Loading,
) {

    /**
     * The order the user has dragged the active accounts into, or null while the database is the
     * only authority on it. A drag is persisted on drop, so between the first move and the write
     * landing the stored order is stale — and even afterwards an unrelated emission (a balance
     * recalculation, a sync pull) can carry the old one. Without this overlay either would snap
     * the rows back out from under the finger.
     */
    private var userOrder: List<AccountId>? = null

    /**
     * The order waiting to be written, or null until something is dropped. Each drop would
     * otherwise start its own write coroutine, and two of those can read the table before either
     * has written it: the second then finds a row already sitting at its target position, skips
     * it, and leaves the list in an order nobody chose. One writer draining a conflated flow
     * keeps the last drop last.
     */
    private val pendingOrder = MutableStateFlow<List<AccountId>?>(null)

    init {
        observeAccounts()
        launch {
            pendingOrder.filterNotNull().collect { order ->
                reorderAccounts(order).onLeft { snackbar.show(Res.string.accounts_manage_action_failed) }
            }
        }
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
            is AccountsManageEvent.OnRemoveAccountClick -> requestDelete(event.accountId)
            AccountsManageEvent.OnDeleteCancel -> dismissDelete()
            AccountsManageEvent.OnDeleteConfirm -> confirmDelete()
            is AccountsManageEvent.OnAccountMove -> moveAccount(from = event.from, to = event.to)
            AccountsManageEvent.OnAccountMoveEnd -> persistUserOrder()
        }
    }

    /**
     * Moves [from] into the slot [to] holds right now. State only — a drag reports a move every
     * half row, and the order is written once, when the row is dropped.
     */
    private fun moveAccount(from: AccountId, to: AccountId) {
        val content = currentState as? AccountsManageState.Content ?: return
        val reordered = content.activeAccounts.moved(from = from, to = to) ?: return
        userOrder = reordered.map { it.id }
        updateState { AccountsManageState.content.activeAccounts.modify(this) { reordered } }
    }

    private fun persistUserOrder() {
        pendingOrder.value = userOrder ?: return
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
                ifLeft = { snackbar.show(Res.string.accounts_manage_action_failed) },
                ifRight = {
                    snackbar.show(
                        message = Res.string.accounts_manage_archived_snackbar,
                        messageArgs = listOf(target.name),
                        actionLabel = Res.string.accounts_manage_archive_undo,
                        onAction = { restoreArchived(target.id) },
                    )
                },
            )
        }
    }

    private fun performRestore(accountId: AccountId) {
        launch { restoreArchived(accountId) }
    }

    private suspend fun restoreArchived(accountId: AccountId) {
        restoreAccount(accountId).onLeft {
            snackbar.show(Res.string.accounts_manage_action_failed)
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
        launch {
            deleteAccount(target.id).fold(
                ifLeft = { snackbar.show(Res.string.accounts_manage_action_failed) },
                ifRight = {
                    snackbar.show(Res.string.accounts_manage_deleted_snackbar, listOf(target.name))
                },
            )
        }
    }

    private fun observeAccounts() {
        launch {
            getAccounts().collect { accounts ->
                val stored = accounts.filterNot { it.archived }
                val activeAccounts = userOrder?.let(stored::orderedBy) ?: stored
                // Once the database agrees with the drag, it is the authority again — including
                // when it agrees only because an account the drag named has since been deleted.
                if (activeAccounts == stored) userOrder = null
                val active = activeAccounts.map { it.toUi() }
                val archived = accounts.filter { it.archived }.map { it.toUi() }
                val totals = activeAccounts.formattedTotalsByCurrency()
                updateState {
                    when (this) {
                        is AccountsManageState.Loading -> AccountsManageState.Content(
                            isEditing = false,
                            activeAccounts = active,
                            archivedAccounts = archived,
                            formattedTotal = totals.firstOrNull(),
                            otherCurrencyTotals = totals.drop(1),
                        )
                        is AccountsManageState.Content -> copy(
                            activeAccounts = active,
                            archivedAccounts = archived,
                            formattedTotal = totals.firstOrNull(),
                            otherCurrencyTotals = totals.drop(1),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The accounts of the receiver arranged the way [order] names them. Accounts [order] says nothing
 * about — created since the drag — keep the position they came in with, after the placed ones.
 */
private fun List<Account>.orderedBy(order: List<AccountId>): List<Account> {
    val positions = order.withIndex().associate { (index, id) -> id to index }
    return sortedBy { positions[it.id] ?: Int.MAX_VALUE }
}

private fun List<AccountManageUi>.moved(from: AccountId, to: AccountId): List<AccountManageUi>? {
    if (from == to) return null
    val fromIndex = indexOfFirst { it.id == from }
    val toIndex = indexOfFirst { it.id == to }
    if (fromIndex < 0 || toIndex < 0) return null
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

private fun Account.toUi() = AccountManageUi(
    id = id,
    name = name,
    type = type,
    formattedBalance = MoneyFormatter.format(balance, currencyCode),
    currency = currencyCode.value,
    archivedOn = archivedAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
)

@optics
sealed interface AccountsManageState {
    data object Loading : AccountsManageState

    @optics
    data class Content(
        val isEditing: Boolean,
        val activeAccounts: List<AccountManageUi>,
        val archivedAccounts: List<AccountManageUi>,
        /** Total for the most-used currency, or null when there are no active accounts. */
        val formattedTotal: String?,
        /**
         * Totals for every other currency in play. Rendered beside [formattedTotal] rather than
         * folded into it — without FX rates a single number would just hide these balances.
         */
        val otherCurrencyTotals: List<String> = emptyList(),
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
    /** Date the account was archived, or null when it is active (or was archived pre-#307). */
    val archivedOn: LocalDate? = null,
)

sealed interface AccountsManageEvent {
    data object OnBackClick : AccountsManageEvent
    data object OnAddAccountClick : AccountsManageEvent
    data object OnToggleEditMode : AccountsManageEvent
    data class OnAccountClick(val accountId: AccountId) : AccountsManageEvent
    data class OnArchiveAccountClick(val accountId: AccountId) : AccountsManageEvent
    data object OnArchiveConfirm : AccountsManageEvent
    data object OnArchiveCancel : AccountsManageEvent
    data class OnRestoreAccountClick(val accountId: AccountId) : AccountsManageEvent
    data class OnRemoveAccountClick(val accountId: AccountId) : AccountsManageEvent
    data object OnDeleteConfirm : AccountsManageEvent
    data object OnDeleteCancel : AccountsManageEvent

    /** [from] was dragged onto the slot [to] occupies right now, among the active accounts. */
    data class OnAccountMove(val from: AccountId, val to: AccountId) : AccountsManageEvent

    /** The dragged row was dropped — write the order the moves have built up. */
    data object OnAccountMoveEnd : AccountsManageEvent
}

sealed interface AccountsManageEffect {
    data object NavigateBack : AccountsManageEffect
    data object NavigateToAccountCreation : AccountsManageEffect
    data class NavigateToAccountDetails(val accountId: AccountId) : AccountsManageEffect
    data class NavigateToAccountEdit(val accountId: AccountId) : AccountsManageEffect
}
