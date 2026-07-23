package com.georgeci.moneysurfer.feature.account.creation

import arrow.optics.optics
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
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
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_created_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_currency_failed_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_updated_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
@Suppress("LongParameterList")
class AccountCreationViewModel(
    private val accountId: AccountId?,
    private val firstRun: Boolean,
    initialType: AccountType,
    private val accountRepository: AccountRepository,
    private val createTransaction: CreateTransactionUseCase,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val getCurrencies: GetCurrenciesUseCase,
    private val updateWorkspaceCurrency: UpdateWorkspaceCurrencyUseCase,
    private val snackbar: SnackbarController,
    private val offlineBuildFlags: OfflineBuildFlags,
) : MviViewModel<AccountCreationState, AccountCreationEvent, AccountCreationEffect>(
    initialState = if (accountId != null) {
        AccountCreationState.Loading(editingAccountId = accountId)
    } else {
        AccountCreationState.Content(
            name = "",
            balance = "",
            // Onboarding passes the kind the user picked; every other entry point falls back
            // to the screen's own default.
            type = initialType,
            currency = DEFAULT_CURRENCY,
            currencies = emptyList(),
            extraFields = emptyList(),
            extraDetailsEnabled = !offlineBuildFlags.isOffline,
            editingAccountId = null,
        )
    },
) {

    private val log = Logger.withTag(TAG)

    private var loadedCurrencies: List<Currency> = emptyList()

    init {
        if (accountId != null) loadAccount(accountId)
        loadCurrencies()
    }

    override fun onEvent(event: AccountCreationEvent) {
        when (event) {
            is AccountCreationEvent.OnNameChanged ->
                updateState {
                    AccountCreationState.content.modify(this) {
                        it.copy(name = event.name, nameTouched = true)
                    }
                }
            is AccountCreationEvent.OnBalanceChanged ->
                updateState {
                    AccountCreationState.content.balance.modify(this) {
                        InitialBalanceInput.sanitize(event.balance)
                    }
                }
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
                    extraDetailsEnabled = !offlineBuildFlags.isOffline,
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
        if (state.balanceError != null) return
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
            val balanceDouble = InitialBalanceInput.parse(state.balance) ?: 0.0
            val currency = state.currency
            val openingBalance = Money.fromDouble(balanceDouble)
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

            if (firstRun) {
                // First-launch step: the currency picked here becomes the workspace base
                // currency, so the Dashboard total speaks the user's currency from day one.
                // The account itself is already saved, so a failure here is reported and the
                // user still moves on — the workspace currency is fixable from settings, and
                // stranding them on the first-run screen would invite a duplicate account.
                updateWorkspaceCurrency(workspaceId, currency).onLeft { err ->
                    log.w { "[firstRun] workspace currency not applied -> $err" }
                    snackbar.show(Res.string.account_creation_currency_failed_snackbar)
                }
                postSideEffect(AccountCreationEffect.NavigateToDashboard)
            } else {
                postSideEffect(AccountCreationEffect.NavigateBack)
            }
        }
    }

    private companion object {
        const val TAG = "AccountCreationVM"
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
        val extraDetailsEnabled: Boolean = true,
        override val editingAccountId: AccountId?,
        /** Whether the user has edited the name field — gates the required-name inline error
         *  so a pristine screen doesn't open covered in red. */
        val nameTouched: Boolean = false,
    ) : AccountCreationState {
        val availableExtraFieldKinds: List<AccountExtraFieldKind>
            get() = AccountExtraFieldKind.entries.filter { kind ->
                extraFields.none { it.kind == kind }
            }

        val currencySymbol: String
            get() = currencies.firstOrNull { it.code == currency }?.symbol ?: currency.value

        /** Whether to show the required-name inline error: the field was edited and left blank. */
        val nameMissing: Boolean
            get() = nameTouched && name.isBlank()

        /** Inline validation error for the opening balance field, or null when it is acceptable. */
        val balanceError: InitialBalanceError?
            get() = InitialBalanceInput.errorFor(balance, type)

        /** Whether the Save action may proceed: a name is present and the balance is valid. */
        val canSave: Boolean
            get() = name.isNotBlank() && balanceError == null

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

    /** First-launch only: the first account is in place, continue into the app. */
    data object NavigateToDashboard : AccountCreationEffect
}
