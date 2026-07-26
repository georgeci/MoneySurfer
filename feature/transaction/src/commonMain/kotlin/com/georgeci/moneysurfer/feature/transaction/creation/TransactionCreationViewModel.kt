package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_created_snackbar
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer_snackbar
import moneysurfer.feature.transaction.generated.resources.transaction_creation_updated_snackbar
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

// ViewModel composes loading + creation + transfer flows; splitting now would push wiring to a holder.
@KoinViewModel
@Suppress("LongParameterList")
class TransactionCreationViewModel(
    private val seed: TransactionCreationSeed?,
    private val accountId: AccountId?,
    private val getAccounts: GetAccountsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getTransactionById: GetTransactionByIdUseCase,
    private val createTransaction: CreateTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val createTransfer: CreateTransferUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val transactionRepository: TransactionRepository,
    private val hostCapabilities: HostCapabilities,
    private val snackbar: SnackbarController,
) : MviViewModel<TransactionCreationState, TransactionCreationEvent, TransactionCreationEffect>(
    initialState = TransactionCreationState.Loading,
) {

    init {
        loadData()
        observeCategoryUsage()
    }

    private var pendingAccountSlot: AccountSlot = AccountSlot.Single

    override fun onEvent(event: TransactionCreationEvent) {
        when (event) {
            is TransactionCreationEvent.OnAmountChanged ->
                updateState { TransactionCreationState.content.amount.modify(this) { event.amount } }
            is TransactionCreationEvent.OnToAmountChanged ->
                updateState { TransactionCreationState.content.toAmount.modify(this) { event.amount } }
            is TransactionCreationEvent.OnNoteChanged ->
                updateState { TransactionCreationState.content.note.modify(this) { event.note } }
            is TransactionCreationEvent.OnAccountSelected ->
                updateState { TransactionCreationState.content.selectedAccount.modify(this) { event.account } }
            is TransactionCreationEvent.OnCategorySelected ->
                updateState { TransactionCreationState.content.selectedCategory.modify(this) { event.category } }
            is TransactionCreationEvent.OnCategoryPicked -> applyPickedCategory(event.id)
            is TransactionCreationEvent.OnAccountPicked -> applyPickedAccount(event.id)
            is TransactionCreationEvent.OnTypeChanged -> changeType(event.type)
            is TransactionCreationEvent.OnDateChanged -> updateState {
                TransactionCreationState.content.timestamp.set(this, event.timestamp)
                    .let { TransactionCreationState.content.pinnedOperationDate.set(it, null) }
            }
            TransactionCreationEvent.OnTodayClick -> updateState {
                TransactionCreationState.content.timestamp
                    .set(this, getCurrentTime().toEpochMilliseconds())
                    .let { TransactionCreationState.content.pinnedOperationDate.set(it, null) }
            }
            TransactionCreationEvent.OnOpenCategoryChooser -> openCategoryChooser()
            TransactionCreationEvent.OnOpenCategoryCreation -> postSideEffect(
                TransactionCreationEffect.NavigateToCategoryCreation,
            )
            TransactionCreationEvent.OnOpenAccountChooser -> openAccountChooser(AccountSlot.Single)
            TransactionCreationEvent.OnOpenFromAccountChooser -> openAccountChooser(AccountSlot.From)
            TransactionCreationEvent.OnOpenToAccountChooser -> openAccountChooser(AccountSlot.To)
            TransactionCreationEvent.OnSwapAccountsClick -> updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.copy(
                    fromAccount = c.toAccount,
                    toAccount = c.fromAccount,
                    amount = c.toAmount,
                    toAmount = c.amount,
                )
            }
            TransactionCreationEvent.OnSaveClick -> saveTransaction()
            TransactionCreationEvent.OnDeleteClick -> updateState {
                TransactionCreationState.content.showDeleteConfirmation.modify(this) { true }
            }
            TransactionCreationEvent.OnDeleteDismissed -> updateState {
                TransactionCreationState.content.showDeleteConfirmation.modify(this) { false }
            }
            TransactionCreationEvent.OnDeleteConfirmed -> handleDelete()
            TransactionCreationEvent.OnBackClick -> postSideEffect(TransactionCreationEffect.NavigateBack)
        }
    }

    private fun openCategoryChooser() {
        val state = currentState as? TransactionCreationState.Content ?: return
        postSideEffect(
            TransactionCreationEffect.NavigateToCategoryChooser(
                selectedCategoryId = state.selectedCategory?.id,
                filterType = state.type.categoryType(),
            ),
        )
    }

    private fun openAccountChooser(slot: AccountSlot) {
        val state = currentState as? TransactionCreationState.Content ?: return
        pendingAccountSlot = slot
        val (selected, excluded) = when (slot) {
            AccountSlot.Single -> state.selectedAccount?.id to null
            AccountSlot.From -> state.fromAccount?.id to state.toAccount?.id
            AccountSlot.To -> state.toAccount?.id to state.fromAccount?.id
        }
        postSideEffect(
            TransactionCreationEffect.NavigateToAccountChooser(
                selectedAccountId = selected,
                excludeAccountId = excluded,
                // Only a single-account pick can still become a transfer; the From/To slots are
                // already inside one. Same gate as the type switch, so the footer never leads
                // somewhere [changeType] would refuse to go.
                showTransferShortcut = slot == AccountSlot.Single &&
                    !state.isEditMode &&
                    state.transferEnabled,
            ),
        )
    }

    private fun changeType(nextType: TransactionTypeUi) = updateState {
        val content = this as? TransactionCreationState.Content ?: return@updateState this
        // Editing an existing single-leg transaction can't morph into a paired transfer in place;
        // ignore the switch so we don't desync the type with the row that's about to be updated.
        // `content.transferEnabled` rather than a fresh `hostCapabilities` read: a QA override landing
        // mid-screen would otherwise leave a rendered Transfer segment that this guard refuses.
        if (nextType == TransactionTypeUi.Transfer && (content.isEditMode || !content.transferEnabled)) {
            return@updateState content
        }
        val nextCategoryType = nextType.categoryType()
        val nextSelected = pickDefaultCategory(content.categories, content.categoryUsageCounts, nextCategoryType)
        val seededFrom = content.fromAccount ?: content.selectedAccount
        val seededTo = content.toAccount
            ?: content.accounts.firstOrNull { it.id != seededFrom?.id }
        content.copy(
            type = nextType,
            selectedCategory = nextSelected,
            displayCategories = buildDisplayCategories(
                categories = content.categories,
                counts = content.categoryUsageCounts,
                type = nextCategoryType,
                selected = nextSelected,
            ),
            fromAccount = if (nextType == TransactionTypeUi.Transfer) seededFrom else content.fromAccount,
            toAccount = if (nextType == TransactionTypeUi.Transfer) seededTo else content.toAccount,
        )
    }

    private fun loadData() {
        launch {
            val accounts = getAccounts().first()
            val categories = getCategories().first()
            val prefillAccount = accountId?.let { id -> accounts.find { it.id == id } }

            val initialType = TransactionTypeUi.Expense
            val initialCategoryType = initialType.categoryType()
            val initialSelected = pickDefaultCategory(categories, emptyMap(), initialCategoryType)

            val baseContent = TransactionCreationState.Content(
                amount = "",
                note = "",
                type = initialType,
                accounts = accounts,
                categories = categories,
                selectedAccount = prefillAccount ?: accounts.firstOrNull(),
                selectedCategory = initialSelected,
                isEditMode = false,
                editingTransactionId = null,
                timestamp = getCurrentTime().toEpochMilliseconds(),
                categoryUsageCounts = emptyMap(),
                displayCategories = buildDisplayCategories(
                    categories = categories,
                    counts = emptyMap(),
                    type = initialCategoryType,
                    selected = initialSelected,
                ),
                transferEnabled = hostCapabilities.transferEnabled,
            )

            val existing = seed?.let { getTransactionById(it.transactionId) }
            updateState {
                if (seed == null || existing == null) {
                    baseContent
                } else {
                    baseContent.seededFrom(seed, existing)
                }
            }
        }
    }

    private fun observeCategoryUsage() {
        launch {
            transactionRepository.getAll().collect { all ->
                val counts = all
                    .mapNotNull { it.categoryId }
                    .groupingBy { it }
                    .eachCount()
                updateState {
                    val content = this as? TransactionCreationState.Content ?: return@updateState this
                    content.copy(
                        categoryUsageCounts = counts,
                        displayCategories = buildDisplayCategories(
                            categories = content.categories,
                            counts = counts,
                            type = content.type.categoryType(),
                            selected = content.selectedCategory,
                        ),
                    )
                }
            }
        }
    }

    private fun applyPickedAccount(picked: AccountId) {
        val content = currentState as? TransactionCreationState.Content ?: return
        val slot = pendingAccountSlot
        val match = content.accounts.find { it.id == picked }
        if (match != null) {
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                applyAccountToSlot(c, match, slot)
            }
            return
        }
        launch {
            val refreshed = getAccounts().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                val withRefreshed = c.copy(accounts = refreshed)
                if (newMatch != null) applyAccountToSlot(withRefreshed, newMatch, slot) else withRefreshed
            }
        }
    }

    private fun applyAccountToSlot(
        content: TransactionCreationState.Content,
        account: Account,
        slot: AccountSlot,
    ): TransactionCreationState.Content = when (slot) {
        AccountSlot.Single -> content.copy(selectedAccount = account)
        AccountSlot.From -> content.copy(fromAccount = account)
        AccountSlot.To -> content.copy(toAccount = account)
    }

    private fun applyPickedCategory(picked: CategoryId) {
        val content = currentState as? TransactionCreationState.Content ?: return
        val match = content.categories.find { it.id == picked }
        if (match != null) {
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.withSelectedCategory(categories = c.categories, selected = match)
            }
            return
        }
        // Categories may not be loaded yet (e.g. just-created entry); refresh once.
        launch {
            val refreshed = getCategories().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.withSelectedCategory(categories = refreshed, selected = newMatch ?: c.selectedCategory)
            }
        }
    }

    private fun TransactionCreationState.Content.withSelectedCategory(
        categories: List<Category>,
        selected: Category?,
    ): TransactionCreationState.Content = copy(
        categories = categories,
        selectedCategory = selected,
        displayCategories = buildDisplayCategories(
            categories = categories,
            counts = categoryUsageCounts,
            type = type.categoryType(),
            selected = selected,
        ),
    )

    private fun saveTransaction() {
        val state = currentState as? TransactionCreationState.Content ?: return
        if (state.isTransfer) {
            saveTransfer(state)
            return
        }
        val account = state.selectedAccount ?: return
        val category = state.selectedCategory ?: return
        val amountDouble = TransactionAmountInput.parse(state.amount)?.takeIf { it > 0 } ?: return

        val money = Money.fromDouble(amountDouble).abs()
        val type = if (state.isExpense) TransactionType.EXPENSE else TransactionType.INCOME

        launch {
            val now = getCurrentTime()
            val zone = TimeZone.currentSystemDefault()
            val operationAt = kotlin.time.Instant.fromEpochMilliseconds(state.timestamp)
            val transaction = Transaction(
                id = state.editingTransactionId ?: TransactionId.uuid(),
                workspaceId = account.workspaceId,
                accountId = account.id,
                money = money,
                currencyCode = account.currencyCode,
                categoryId = category.id,
                note = state.note,
                // This rebuilds the whole row rather than patching the stored one, so every field
                // the form does not edit has to be handed back explicitly — omitting one resets it.
                merchant = state.preserved.merchant,
                tags = state.preserved.tags,
                operationAt = operationAt,
                operationDate = state.pinnedOperationDate
                    ?: operationAt.toLocalDateTime(zone).date,
                type = type,
                status = state.preserved.status,
                createdAt = state.editingCreatedAt ?: now,
                updatedAt = now,
                transferId = state.preserved.transferId,
                recurringRuleId = state.preserved.recurringRuleId,
            )

            if (state.isEditMode) {
                updateTransaction(transaction)
                snackbar.show(Res.string.transaction_creation_updated_snackbar)
            } else {
                createTransaction(transaction)
                snackbar.show(Res.string.transaction_creation_created_snackbar)
            }

            postSideEffect(TransactionCreationEffect.NavigateBack)
        }
    }

    private fun saveTransfer(state: TransactionCreationState.Content) {
        val plan = buildTransferPlan(state) ?: return
        launch {
            val zone = TimeZone.currentSystemDefault()
            val operationAt = kotlin.time.Instant.fromEpochMilliseconds(state.timestamp)
            createTransfer(
                CreateTransferUseCase.Params(
                    from = plan.from,
                    to = plan.to,
                    fromMoney = Money.fromDouble(plan.fromAmount).abs(),
                    toMoney = Money.fromDouble(plan.toAmount).abs(),
                    note = state.note,
                    operationAt = operationAt,
                    operationDate = state.pinnedOperationDate
                        ?: operationAt.toLocalDateTime(zone).date,
                ),
            )
            snackbar.show(Res.string.transaction_creation_transfer_snackbar)
            postSideEffect(TransactionCreationEffect.NavigateBack)
        }
    }

    /**
     * Deletes the row being edited, exactly the way the details screen does — same undo snackbar,
     * restoring through [applyTransactionChange] so the account balance comes back with it.
     *
     * Guarded on [TransactionCreationState.Content.editingTransactionId]: there is nothing to
     * delete while creating or duplicating, and the screen hides the action there.
     */
    private fun handleDelete() {
        val editingId = (currentState as? TransactionCreationState.Content)?.editingTransactionId ?: return
        launch {
            val deleted = deleteTransaction(editingId)
            if (deleted != null) {
                snackbar.show(
                    message = Res.string.transaction_details_deleted_snackbar,
                    actionLabel = Res.string.transaction_details_delete_undo,
                    onAction = { applyTransactionChange(old = null, new = deleted) },
                )
            }
            postSideEffect(TransactionCreationEffect.NavigateBackAfterDelete)
        }
    }

    private data class TransferPlan(
        val from: Account,
        val to: Account,
        val fromAmount: Double,
        val toAmount: Double,
    )

    @Suppress("ReturnCount") // Sequential null/range guards stay clearer than a single nested expression.
    private fun buildTransferPlan(state: TransactionCreationState.Content): TransferPlan? {
        val from = state.fromAccount ?: return null
        val to = state.toAccount ?: return null
        if (from.id == to.id) return null
        val fromAmount = TransactionAmountInput.parse(state.amount)?.takeIf { it > 0 } ?: return null
        val toAmount = if (state.crossCurrency) {
            TransactionAmountInput.parse(state.toAmount)?.takeIf { it > 0 } ?: return null
        } else {
            fromAmount
        }
        return TransferPlan(from, to, fromAmount, toAmount)
    }
}
