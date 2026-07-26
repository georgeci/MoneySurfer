package com.georgeci.moneysurfer.feature.account.creation

import arrow.optics.optics
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.AccountExtraDetails
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.CreateAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_created_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_currency_failed_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_updated_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_action_failed
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
    private val getAccountById: GetAccountByIdUseCase,
    private val createAccount: CreateAccountUseCase,
    private val updateAccount: UpdateAccountUseCase,
    private val createTransaction: CreateTransactionUseCase,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val getCurrencies: GetCurrenciesUseCase,
    private val updateWorkspaceCurrency: UpdateWorkspaceCurrencyUseCase,
    private val snackbar: SnackbarController,
    private val hostCapabilities: HostCapabilities,
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
            extraDetailsEnabled = !hostCapabilities.isOffline,
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
            is AccountCreationEvent.OnExtraFieldValueChanged -> updateExtraField(event.key, event.value)
            is AccountCreationEvent.OnAddExtraField -> addExtraField(event.key.name)
            is AccountCreationEvent.OnAddCustomExtraField -> addExtraField(event.name)
            is AccountCreationEvent.OnRemoveExtraField -> removeExtraField(event.key)
            AccountCreationEvent.OnSaveClick -> saveAccount()
            AccountCreationEvent.OnBackClick -> postSideEffect(AccountCreationEffect.NavigateBack)
        }
    }

    private fun loadAccount(id: AccountId) {
        launch {
            val account = getAccountById(id)
            updateState {
                AccountCreationState.Content(
                    name = account?.name.orEmpty(),
                    balance = "",
                    type = account?.type ?: AccountType.BANK,
                    currency = account?.currencyCode ?: DEFAULT_CURRENCY,
                    currencies = loadedCurrencies,
                    // Loaded even in the offline build, where the section is hidden: the rows are
                    // still part of the account, and saving an edit must not silently drop them.
                    extraFields = account?.extraDetails.orEmpty()
                        .map { AccountExtraField(key = it.key, value = it.value) },
                    extraDetailsEnabled = !hostCapabilities.isOffline,
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

    private fun updateExtraField(key: String, value: String) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            fields.map { if (it.key == key) it.copy(value = value) else it }
        }
    }

    /**
     * Adds an empty row for [rawKey] — a well-known key name from a chip, or a label the user
     * typed into "Custom field…". Blank names, names already on the form (case-insensitively, so
     * a custom "iban" cannot shadow the IBAN chip) and additions past [AccountExtraDetails.MAX_DETAILS]
     * are ignored: the chip or dialog simply does nothing rather than reporting an error the
     * design has no room for.
     */
    private fun addExtraField(rawKey: String) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            val key = rawKey.replace(WHITESPACE_RUN, " ").trim().take(AccountExtraDetails.MAX_KEY_LENGTH)
            val rejected = key.isEmpty() ||
                fields.size >= AccountExtraDetails.MAX_DETAILS ||
                fields.any { it.key.equals(key, ignoreCase = true) }
            if (rejected) fields else fields + AccountExtraField(key = key, value = "")
        }
    }

    private fun removeExtraField(key: String) = updateState {
        AccountCreationState.content.extraFields.modify(this) { fields ->
            fields.filterNot { it.key == key }
        }
    }

    private fun saveAccount() {
        val state = currentState as? AccountCreationState.Content ?: return
        if (state.name.isBlank() || state.balanceError != null) return
        launch(
            onError = ::handleSaveError,
        ) {
            val trimmedName = state.name.trim()
            if (state.isEditMode) {
                val existingId = state.editingAccountId ?: return@launch
                updateAccount(existingId) { existing ->
                    existing.copy(
                        name = trimmedName,
                        type = state.type,
                        extraDetails = state.savedExtraDetails(),
                    )
                }.onLeft { err ->
                    log.w { "[edit] not saved id=${existingId.value} -> $err" }
                    snackbar.show(Res.string.accounts_manage_action_failed)
                    return@launch
                }
                snackbar.show(Res.string.account_creation_updated_snackbar, listOf(trimmedName))
                postSideEffect(AccountCreationEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.first() ?: run {
                log.w { "[save] no current workspace pinned — cannot create account" }
                snackbar.show(Res.string.accounts_manage_action_failed)
                return@launch
            }
            val balanceDouble = InitialBalanceInput.parse(state.balance) ?: 0.0
            val currency = state.currency
            val openingBalance = Money.fromDouble(balanceDouble)
            val newAccountId = AccountId.uuid()
            createAccount(
                Account(
                    id = newAccountId,
                    workspaceId = workspaceId,
                    name = trimmedName,
                    type = state.type,
                    currencyCode = currency,
                    balance = Money.zero(),
                    extraDetails = state.savedExtraDetails(),
                ),
            ).onLeft { err ->
                log.w { "[create] not saved -> $err" }
                snackbar.show(Res.string.accounts_manage_action_failed)
                return@launch
            }

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
            finishAccountCreation(workspaceId, currency)
        }
    }

    private fun handleSaveError(error: Throwable) {
        log.e(error) { "[save] failed" }
        snackbar.show(Res.string.accounts_manage_action_failed)
    }

    private suspend fun finishAccountCreation(workspaceId: WorkspaceId, currency: CurrencyCode) {
        if (!firstRun) {
            postSideEffect(AccountCreationEffect.NavigateBack)
            return
        }

        // The account is already saved, so a workspace-currency failure is reported without
        // stranding the user on the first-run screen and inviting a duplicate account.
        updateWorkspaceCurrency(workspaceId, currency).onLeft { err ->
            log.w { "[firstRun] workspace currency not applied -> $err" }
            snackbar.show(Res.string.account_creation_currency_failed_snackbar)
        }
        postSideEffect(AccountCreationEffect.NavigateToDashboard)
    }

    /** The form's extra-detail rows as they should be stored: normalized, blanks dropped. */
    private fun AccountCreationState.Content.savedExtraDetails(): List<AccountExtraDetail> =
        AccountExtraDetails.normalize(
            extraFields.map { AccountExtraDetail(key = it.key, value = it.value) },
        )

    private companion object {
        const val TAG = "AccountCreationVM"
        val DEFAULT_CURRENCY = CurrencyCode("EUR")
        val WHITESPACE_RUN = Regex("\\s+")
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
        /**
         * Well-known keys still offered as chips. Matched case-insensitively, the same way
         * [addExtraField] rejects duplicates — otherwise a custom field named "iban" would leave
         * the IBAN chip on screen as a control that silently does nothing when tapped.
         */
        val availableExtraFieldKeys: List<AccountExtraDetailKey>
            get() = AccountExtraDetailKey.entries.filter { key ->
                extraFields.none { it.key.equals(key.name, ignoreCase = true) }
            }

        /** Whether another row still fits — gates both the chips and the "Custom field…" action. */
        val canAddExtraField: Boolean
            get() = extraFields.size < AccountExtraDetails.MAX_DETAILS

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

/**
 * A row in the "Extra details" section while it is being edited. [key] is a well-known
 * [AccountExtraDetailKey] name or the label the user typed, and doubles as the row's identity in
 * the event stream — blank values are still rows here, and only get dropped on save.
 */
data class AccountExtraField(
    val key: String,
    val value: String,
) {
    val wellKnownKey: AccountExtraDetailKey?
        get() = AccountExtraDetails.wellKnownKey(key)
}

sealed interface AccountCreationEvent {
    data class OnNameChanged(val name: String) : AccountCreationEvent
    data class OnBalanceChanged(val balance: String) : AccountCreationEvent
    data class OnTypeChanged(val type: AccountType) : AccountCreationEvent
    data class OnCurrencyChanged(val currency: CurrencyCode) : AccountCreationEvent
    data class OnAddExtraField(val key: AccountExtraDetailKey) : AccountCreationEvent
    data class OnAddCustomExtraField(val name: String) : AccountCreationEvent
    data class OnRemoveExtraField(val key: String) : AccountCreationEvent
    data class OnExtraFieldValueChanged(val key: String, val value: String) : AccountCreationEvent
    data object OnSaveClick : AccountCreationEvent
    data object OnBackClick : AccountCreationEvent
}

sealed interface AccountCreationEffect {
    data object NavigateBack : AccountCreationEffect

    /** First-launch only: the first account is in place, continue into the app. */
    data object NavigateToDashboard : AccountCreationEffect
}
