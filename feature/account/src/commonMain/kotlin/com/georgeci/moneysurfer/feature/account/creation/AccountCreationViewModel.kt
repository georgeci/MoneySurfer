package com.georgeci.moneysurfer.feature.account.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_created_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_updated_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AccountCreationViewModel(
    private val accountId: AccountId?,
    private val accountRepository: AccountRepository,
    private val createTransaction: CreateTransactionUseCase,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val getCurrencies: GetCurrenciesUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<AccountCreationState, AccountCreationEvent, AccountCreationEffect>(
    initialState = if (accountId != null) {
        AccountCreationState.Loading(editingAccountId = accountId)
    } else {
        AccountCreationState.Content(
            name = "",
            balance = "",
            type = AccountType.SAVINGS,
            currency = DEFAULT_CURRENCY,
            currencies = emptyList(),
            extraFields = emptyList(),
            editingAccountId = null,
        )
    },
) {

    private var loadedCurrencies: List<Currency> = emptyList()

    init {
        if (accountId != null) loadAccount(accountId)
        loadCurrencies()
    }

    override fun onEvent(event: AccountCreationEvent) {
        when (event) {
            is AccountCreationEvent.OnNameChanged ->
                updateState { AccountCreationState.content.name.modify(this) { event.name } }
            is AccountCreationEvent.OnBalanceChanged ->
                updateState { AccountCreationState.content.balance.modify(this) { event.balance } }
            is AccountCreationEvent.OnTypeChanged ->
                updateState { AccountCreationState.content.type.modify(this) { event.type } }
            is AccountCreationEvent.OnCurrencyChanged ->
                updateState { AccountCreationState.content.currency.modify(this) { event.currency } }
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
                    currency = account?.currencyCode ?: DEFAULT_CURRENCY,
                    currencies = loadedCurrencies,
                    extraFields = emptyList(),
                    editingAccountId = id,
                )
            }
        }
    }

    private fun loadCurrencies() {
        launch {
            val currencies = getCurrencies().first()
            loadedCurrencies = currencies
            updateState {
                when (this) {
                    is AccountCreationState.Loading -> this
                    is AccountCreationState.Content -> copy(
                        currencies = currencies,
                        currency = currency.takeIf { code ->
                            currencies.any { it.code == code }
                        } ?: currencies.firstOrNull()?.code ?: currency,
                    )
                }
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
            val trimmedName = state.name.trim()
            if (state.isEditMode) {
                val existingId = state.editingAccountId ?: return@launch
                val existing = accountRepository.getById(existingId) ?: return@launch
                accountRepository.update(
                    existing.copy(
                        name = trimmedName,
                        type = state.type,
                    ),
                )
                snackbar.show(Res.string.account_creation_updated_snackbar, listOf(trimmedName))
                postSideEffect(AccountCreationEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.flow.first() ?: return@launch
            val balanceDouble = state.balance.toDoubleOrNull() ?: 0.0
            val currency = state.currency
            val openingBalance = Money.fromDouble(balanceDouble).abs()
            val newAccountId = AccountId.uuid()
            accountRepository.insert(
                Account(
                    id = newAccountId,
                    workspaceId = workspaceId,
                    name = trimmedName,
                    type = state.type,
                    currencyCode = currency,
                    balance = Money.zero(),
                ),
            )

            if (!openingBalance.isZero()) {
                val now = getCurrentTime()
                val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
                createTransaction(
                    Transaction(
                        id = TransactionId.uuid(),
                        workspaceId = workspaceId,
                        accountId = newAccountId,
                        money = openingBalance,
                        currencyCode = currency,
                        categoryId = null,
                        note = "",
                        operationAt = now,
                        operationDate = now.toLocalDateTime(zone).date,
                        type = TransactionType.OPENING_BALANCE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            snackbar.show(Res.string.account_creation_created_snackbar, listOf(trimmedName))
            postSideEffect(AccountCreationEffect.NavigateBack)
        }
    }

    private companion object {
        val DEFAULT_CURRENCY = CurrencyCode("EUR")
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
        val currency: CurrencyCode,
        val currencies: List<Currency>,
        val extraFields: List<AccountExtraField>,
        override val editingAccountId: AccountId?,
    ) : AccountCreationState {
        val availableExtraFieldKinds: List<AccountExtraFieldKind>
            get() = AccountExtraFieldKind.entries.filter { kind ->
                extraFields.none { it.kind == kind }
            }

        val currencySymbol: String
            get() = currencies.firstOrNull { it.code == currency }?.symbol ?: currency.value

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
    data class OnCurrencyChanged(val currency: CurrencyCode) : AccountCreationEvent
    data class OnAddExtraField(val kind: AccountExtraFieldKind) : AccountCreationEvent
    data class OnRemoveExtraField(val kind: AccountExtraFieldKind) : AccountCreationEvent
    data class OnExtraFieldValueChanged(val kind: AccountExtraFieldKind, val value: String) : AccountCreationEvent
    data object OnSaveClick : AccountCreationEvent
    data object OnBackClick : AccountCreationEvent
}

sealed interface AccountCreationEffect {
    data object NavigateBack : AccountCreationEffect
}
