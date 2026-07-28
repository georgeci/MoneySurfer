package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.CreateSplitTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_created_snackbar
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_snackbar
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
    /**
     * Open on the Transfer tab, for a caller that already knows that is what the user asked for.
     * Read once while loading rather than applied as an event from the screen: a route argument is
     * true for the whole lifetime of the entry, so replaying it on every composition would undo a
     * type the user has since changed by hand (after a rotation, say).
     */
    private val openAsTransfer: Boolean,
    private val getAccounts: GetAccountsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getTransactionById: GetTransactionByIdUseCase,
    private val createTransaction: CreateTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val createTransfer: CreateTransferUseCase,
    private val createSplitTransaction: CreateSplitTransactionUseCase,
    private val deleteWithUndo: DeleteTransactionWithUndo,
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

    /** What the category chooser was last opened for — see [CategorySlot]. */
    private var pendingCategorySlot: CategorySlot = CategorySlot.Single

    /** Hands out the split lines' stable keys; never reused within one screen. */
    private var splitLineKeys: Int = 0

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
            TransactionCreationEvent.OnOpenCategoryChooser -> openCategoryChooser(CategorySlot.Single)
            TransactionCreationEvent.OnOpenCategoryCreation -> postSideEffect(
                TransactionCreationEffect.NavigateToCategoryCreation,
            )
            TransactionCreationEvent.OnOpenAccountChooser -> openAccountChooser(AccountSlot.Single)
            TransactionCreationEvent.OnOpenFromAccountChooser -> openAccountChooser(AccountSlot.From)
            TransactionCreationEvent.OnOpenToAccountChooser -> openAccountChooser(AccountSlot.To)
            TransactionCreationEvent.OnSwapAccountsClick -> updateContent { content ->
                content.copy(
                    fromAccount = content.toAccount,
                    toAccount = content.fromAccount,
                    amount = content.toAmount,
                    toAmount = content.amount,
                )
            }
            TransactionCreationEvent.OnSplitToggled ->
                updateContent { it.withSplitToggled { splitLineKeys++ } }
            TransactionCreationEvent.OnSplitLineAdded ->
                updateContent { it.withSplitLineAdded(key = splitLineKeys++) }
            is TransactionCreationEvent.OnSplitLineRemoved ->
                updateContent { it.withSplitLineRemoved(key = event.key) }
            is TransactionCreationEvent.OnSplitLineAmountChanged ->
                updateContent { it.withSplitLineAmount(key = event.key, amount = event.amount) }
            is TransactionCreationEvent.OnOpenSplitLineCategoryChooser ->
                openCategoryChooser(CategorySlot.SplitLine(event.key))
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

    /**
     * Reduces the loaded form, leaving [TransactionCreationState.Loading] alone — an event that
     * arrives before the screen has loaded has no form to change.
     *
     * A `when` over the sealed state rather than the `as?` this used to repeat at every call site:
     * one place to state the rule, and no cast for a static analyser to squint at (SonarCloud reads
     * `this` inside a `STATE.() -> STATE` reducer as the ViewModel and calls the cast impossible).
     */
    private fun updateContent(
        block: (TransactionCreationState.Content) -> TransactionCreationState.Content,
    ) = updateState {
        when (this) {
            is TransactionCreationState.Content -> block(this)
            TransactionCreationState.Loading -> this
        }
    }

    private fun openCategoryChooser(slot: CategorySlot) {
        val state = currentState as? TransactionCreationState.Content ?: return
        pendingCategorySlot = slot
        val selected = when (slot) {
            CategorySlot.Single -> state.selectedCategory?.id
            is CategorySlot.SplitLine ->
                state.splitLines.firstOrNull { it.key == slot.key }?.category?.id
        }
        postSideEffect(
            TransactionCreationEffect.NavigateToCategoryChooser(
                selectedCategoryId = selected,
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
            // The split editor's lines carry categories of the *old* type, and every category
            // belongs to one side. Keeping them would write income legs filed under expense
            // categories — money the spend aggregates (which filter on `type = 'EXPENSE'`) would
            // never see, under a category whose own screen would then list income. The lines and
            // their amounts survive; only a category the new type cannot use is dropped, which
            // leaves Save disabled until the user picks a replacement.
            splitLines = content.splitLines.map { line ->
                line.copy(category = line.category?.takeIf { it.type == nextCategoryType })
            },
        )
    }

    private fun loadData() {
        launch {
            val accounts = getAccounts().first()
            val categories = getCategories().first()
            val prefillAccount = accountId?.let { id -> accounts.find { it.id == id } }

            // Same three gates the type switch applies: a seeded screen is an edit or a duplicate
            // of a single-leg row, and a build with transfers off has no Transfer tab to open on.
            val openingTransfer = openAsTransfer && seed == null && hostCapabilities.transferEnabled
            val initialType = if (openingTransfer) TransactionTypeUi.Transfer else TransactionTypeUi.Expense
            val initialCategoryType = initialType.categoryType()
            val initialSelected = pickDefaultCategory(categories, emptyMap(), initialCategoryType)
            val initialAccount = prefillAccount ?: accounts.firstOrNull()

            val baseContent = TransactionCreationState.Content(
                amount = "",
                note = "",
                type = initialType,
                accounts = accounts,
                categories = categories,
                selectedAccount = initialAccount,
                // Seeded the way [changeType] seeds them, so arriving on the Transfer tab by route
                // and reaching it by tapping the segment leave the same two slots filled.
                fromAccount = if (openingTransfer) initialAccount else null,
                toAccount = if (openingTransfer) accounts.firstOrNull { it.id != initialAccount?.id } else null,
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
                c.withAccountInSlot(match, slot)
            }
            return
        }
        launch {
            val refreshed = getAccounts().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                val withRefreshed = c.copy(accounts = refreshed)
                if (newMatch != null) withRefreshed.withAccountInSlot(newMatch, slot) else withRefreshed
            }
        }
    }

    private fun applyPickedCategory(picked: CategoryId) {
        val content = currentState as? TransactionCreationState.Content ?: return
        val slot = pendingCategorySlot
        val match = content.categories.find { it.id == picked }
        if (match != null) {
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.withPickedCategory(categories = c.categories, selected = match, slot = slot)
            }
            return
        }
        // Categories may not be loaded yet (e.g. just-created entry); refresh once.
        launch {
            val refreshed = getCategories().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.withPickedCategory(
                    categories = refreshed,
                    selected = newMatch ?: c.categoryInSlot(slot),
                    slot = slot,
                )
            }
        }
    }

    private fun saveTransaction() {
        val state = currentState as? TransactionCreationState.Content ?: return
        if (state.isTransfer) {
            saveTransfer(state)
            return
        }
        if (state.isSplit) {
            saveSplit(state)
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
                splitId = state.preserved.splitId,
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

    /**
     * Writes the split editor's lines as one receipt: N sibling rows sharing a split id, each with
     * its own category and slice of the amount.
     *
     * Guarded twice on purpose — the Save button is disabled unless [isSplitComplete][
     * TransactionCreationState.Content.isSplitComplete] holds, and the use case rejects a group it
     * could not write. The state's own check is what keeps a half-filled editor from reaching a
     * `require` and crashing the screen.
     */
    private fun saveSplit(state: TransactionCreationState.Content) {
        val account = state.selectedAccount ?: return
        if (!state.isSplitComplete) return
        val legs = state.splitLegs()

        launch {
            val zone = TimeZone.currentSystemDefault()
            val operationAt = kotlin.time.Instant.fromEpochMilliseconds(state.timestamp)
            createSplitTransaction(
                CreateSplitTransactionUseCase.Params(
                    account = account,
                    legs = legs,
                    note = state.note,
                    operationAt = operationAt,
                    operationDate = state.pinnedOperationDate
                        ?: operationAt.toLocalDateTime(zone).date,
                    type = if (state.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                ),
            )
            snackbar.show(Res.string.transaction_creation_split_snackbar)
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
     * Deletes the row being edited, exactly the way the details screen and the lists do — the same
     * [DeleteTransactionWithUndo], so the undo snackbar and what a delete takes with it are one
     * behaviour rather than a per-screen approximation of one.
     *
     * Guarded on [TransactionCreationState.Content.editingTransactionId]: there is nothing to
     * delete while creating or duplicating, and the screen hides the action there.
     */
    private fun handleDelete() {
        val editingId = (currentState as? TransactionCreationState.Content)?.editingTransactionId ?: return
        launch {
            deleteWithUndo(
                transactionId = editingId,
                message = Res.string.transaction_details_deleted_snackbar,
                undoLabel = Res.string.transaction_details_delete_undo,
            )
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
