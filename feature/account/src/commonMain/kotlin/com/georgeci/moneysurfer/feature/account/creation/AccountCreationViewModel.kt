package com.georgeci.moneysurfer.feature.account.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountCreationViewModel(
    private val accountId: AccountId?,
    private val accountRepository: AccountRepository,
    private val createTransaction: CreateTransactionUseCase,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
) : MviViewModel<AccountCreationState, AccountCreationEvent, AccountCreationEffect>(
    initialState = if (accountId != null) {
        AccountCreationState.Loading(editingAccountId = accountId)
    } else {
        AccountCreationState.Content(
            name = "",
            balance = "",
            type = AccountType.SAVINGS,
            extraFields = emptyList(),
            editingAccountId = null,
        )
    },
) {

    init {
        if (accountId != null) loadAccount(accountId)
    }

    override fun onEvent(event: AccountCreationEvent) {
        when (event) {
            is AccountCreationEvent.OnNameChanged ->
                updateState { AccountCreationState.content.name.modify(this) { event.name } }
            is AccountCreationEvent.OnBalanceChanged ->
                updateState { AccountCreationState.content.balance.modify(this) { event.balance } }
            is AccountCreationEvent.OnTypeChanged ->
                updateState { AccountCreationState.content.type.modify(this) { event.type } }
            is AccountCreationEvent.OnExtraFieldValueChanged -> updateExtraField(event.kind, event.value)
            is AccountCreationEvent.OnAddExtraField -> addExtraField(event.kind)
            is AccountCreationEvent.OnRemoveExtraField -> removeExtraField(event.kind)
            AccountCreationEvent.OnSaveClick -> saveAccount()
            AccountCreationEvent.OnBackClick -> postSideEffect(AccountCreationEffect.NavigateBack)
        }
    }

    private fun loadAccount(id: AccountId) {
        launch {
            val account = accountRepository.getById(id)
            updateState {
                AccountCreationState.Content(
                    name = account?.name.orEmpty(),
                    balance = "",
                    type = account?.type ?: AccountType.SAVINGS,
                    extraFields = emptyList(),
                    editingAccountId = id,
                )
            }
        }
    }

    private fun updateExtraField(kind: AccountExtraFieldKind, value: String) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            fields.map { if (it.kind == kind) it.copy(value = value) else it }
        }
    }

    private fun addExtraField(kind: AccountExtraFieldKind) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            if (fields.any { it.kind == kind }) fields else fields + AccountExtraField(kind = kind, value = "")
        }
    }

    private fun removeExtraField(kind: AccountExtraFieldKind) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            fields.filterNot { it.kind == kind }
        }
    }

    private fun saveAccount() {
        val state = currentState as? AccountCreationState.Content ?: return
        if (state.name.isBlank()) return
        launch {
            if (state.isEditMode) {
                val existingId = state.editingAccountId ?: return@launch
                val existing = accountRepository.getById(existingId) ?: return@launch
                accountRepository.update(
                    existing.copy(
                        name = state.name.trim(),
                        type = state.type,
                    ),
                )
                postSideEffect(AccountCreationEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.flow.first() ?: return@launch
            val balanceDouble = state.balance.toDoubleOrNull() ?: 0.0
            val currency = CurrencyCode("EUR")
            val openingBalance = Money.fromDouble(balanceDouble).abs()
            val newAccountId = AccountId.uuid()
            accountRepository.insert(
                Account(
                    id = newAccountId,
                    workspaceId = workspaceId,
                    name = state.name.trim(),
                    type = state.type,
                    currencyCode = currency,
                    balance = Money.zero(),
                ),
            )

            if (!openingBalance.isZero()) {
                createTransaction(
                    Transaction(
                        id = TransactionId.uuid(),
                        workspaceId = workspaceId,
                        accountId = newAccountId,
                        money = openingBalance,
                        currencyCode = currency,
                        categoryId = null,
                        note = "",
                        operationAt = getCurrentTime(),
                        type = TransactionType.OPENING_BALANCE,
                    ),
                )
            }

            postSideEffect(AccountCreationEffect.NavigateBack)
        }
    }
}

@optics
sealed interface AccountCreationState {
    val editingAccountId: AccountId?
    val isEditMode: Boolean
        get() = editingAccountId != null

    @optics
    data class Loading(override val editingAccountId: AccountId?) : AccountCreationState {
        companion object
    }

    @optics
    data class Content(
        val name: String,
        val balance: String,
        val type: AccountType,
        val extraFields: List<AccountExtraField>,
        override val editingAccountId: AccountId?,
    ) : AccountCreationState {
        val availableExtraFieldKinds: List<AccountExtraFieldKind>
            get() = AccountExtraFieldKind.entries.filter { kind ->
                extraFields.none { it.kind == kind }
            }

        companion object
    }

    companion object
}

enum class AccountExtraFieldKind { IBAN, DESCRIPTION, BIC, CARD_LAST4, BANK_URL, BRANCH_PHONE }

data class AccountExtraField(
    val kind: AccountExtraFieldKind,
    val value: String,
)

sealed interface AccountCreationEvent {
    data class OnNameChanged(val name: String) : AccountCreationEvent
    data class OnBalanceChanged(val balance: String) : AccountCreationEvent
    data class OnTypeChanged(val type: AccountType) : AccountCreationEvent
    data class OnAddExtraField(val kind: AccountExtraFieldKind) : AccountCreationEvent
    data class OnRemoveExtraField(val kind: AccountExtraFieldKind) : AccountCreationEvent
    data class OnExtraFieldValueChanged(val kind: AccountExtraFieldKind, val value: String) : AccountCreationEvent
    data object OnSaveClick : AccountCreationEvent
    data object OnBackClick : AccountCreationEvent
}

sealed interface AccountCreationEffect {
    data object NavigateBack : AccountCreationEffect
}
