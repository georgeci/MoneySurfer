package com.georgeci.moneysurfer.feature.transaction.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
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
import kotlinx.datetime.LocalDate
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
        val filter = if (state.type == TransactionTypeUi.Income) CategoryType.INCOME else CategoryType.EXPENSE
        postSideEffect(
            TransactionCreationEffect.NavigateToCategoryChooser(
                selectedCategoryId = state.selectedCategory?.id,
                filterType = filter,
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
        val nextCategoryType = if (nextType == TransactionTypeUi.Income) {
            CategoryType.INCOME
        } else {
            CategoryType.EXPENSE
        }
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
            val initialCategoryType = CategoryType.EXPENSE
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

    /**
     * Fills the blank form from an existing transaction.
     *
     * In [TransactionCreationSeed.Mode.Edit] that means the row itself, identity and timestamps
     * included. A duplicate copies only what the user typed — no id, no `createdAt`, and today's
     * date rather than the original's: it is a new transaction that merely starts out looking
     * like an old one.
     */
    private fun TransactionCreationState.Content.seededFrom(
        seed: TransactionCreationSeed,
        transaction: Transaction,
    ): TransactionCreationState.Content {
        val account = accounts.find { it.id == transaction.accountId }
        val category = categories.find { it.id == transaction.categoryId }
        val resolvedType = when (transaction.type) {
            TransactionType.INCOME -> TransactionTypeUi.Income
            else -> TransactionTypeUi.Expense
        }
        val activeCategoryType = if (resolvedType == TransactionTypeUi.Income) {
            CategoryType.INCOME
        } else {
            CategoryType.EXPENSE
        }
        val resolvedSelected = category ?: selectedCategory
        val editing = seed.mode == TransactionCreationSeed.Mode.Edit
        return copy(
            amount = transaction.money.toAmountInput(),
            note = transaction.note,
            type = resolvedType,
            selectedAccount = account ?: selectedAccount,
            selectedCategory = resolvedSelected,
            timestamp = if (editing) transaction.operationAt.toEpochMilliseconds() else timestamp,
            isEditMode = editing,
            editingTransactionId = seed.transactionId.takeIf { editing },
            editingCreatedAt = transaction.createdAt.takeIf { editing },
            pinnedOperationDate = transaction.operationDate.takeIf { editing },
            editIdentity = if (editing) identityOf(transaction, category) else null,
            preserved = if (editing) {
                PreservedTransactionFields.of(transaction)
            } else {
                // A duplicate keeps what the user typed about the counterparty, but is a fresh
                // manual entry: it inherits neither the transfer pairing (a second leg would be
                // missing), the recurring rule that generated the original, nor its planned state.
                PreservedTransactionFields(merchant = transaction.merchant, tags = transaction.tags)
            },
            displayCategories = buildDisplayCategories(
                categories = categories,
                counts = categoryUsageCounts,
                type = activeCategoryType,
                selected = resolvedSelected,
            ),
        )
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
                    val activeCategoryType = if (content.type == TransactionTypeUi.Income) {
                        CategoryType.INCOME
                    } else {
                        CategoryType.EXPENSE
                    }
                    content.copy(
                        categoryUsageCounts = counts,
                        displayCategories = buildDisplayCategories(
                            categories = content.categories,
                            counts = counts,
                            type = activeCategoryType,
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
                val activeCategoryType = if (c.type == TransactionTypeUi.Income) {
                    CategoryType.INCOME
                } else {
                    CategoryType.EXPENSE
                }
                c.copy(
                    selectedCategory = match,
                    displayCategories = buildDisplayCategories(
                        categories = c.categories,
                        counts = c.categoryUsageCounts,
                        type = activeCategoryType,
                        selected = match,
                    ),
                )
            }
            return
        }
        // Categories may not be loaded yet (e.g. just-created entry); refresh once.
        launch {
            val refreshed = getCategories().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                val activeCategoryType = if (c.type == TransactionTypeUi.Income) {
                    CategoryType.INCOME
                } else {
                    CategoryType.EXPENSE
                }
                val resolvedSelected = newMatch ?: c.selectedCategory
                c.copy(
                    categories = refreshed,
                    selectedCategory = resolvedSelected,
                    displayCategories = buildDisplayCategories(
                        categories = refreshed,
                        counts = c.categoryUsageCounts,
                        type = activeCategoryType,
                        selected = resolvedSelected,
                    ),
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

/** Amount as the text field spells it — a whole number keeps no trailing `.0`. */
private fun Money.toAmountInput(): String {
    val major = minor / Money.MINOR_PER_MAJOR
    return if (major == major.toLong().toDouble()) major.toLong().toString() else major.toString()
}

/**
 * The stored row, frozen for the identity band — the design's `04b · Edit transaction`.
 *
 * Read from [transaction] rather than from the live form on purpose: the band answers "which
 * transaction am I editing", so it has to keep saying what was opened even after the amount or
 * the note has been typed over. A transfer leg is labelled as a transfer and left unsigned,
 * matching the details screen.
 */
private fun identityOf(transaction: Transaction, category: Category?): TransactionEditIdentity {
    val formatted = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode)
    return TransactionEditIdentity(
        reference = transaction.id.reference,
        type = when {
            transaction.transferId != null -> TransactionTypeUi.Transfer
            transaction.type == TransactionType.INCOME -> TransactionTypeUi.Income
            else -> TransactionTypeUi.Expense
        },
        note = transaction.note.ifBlank { transaction.merchant },
        formattedAmount = when {
            transaction.transferId != null -> formatted
            transaction.type == TransactionType.INCOME -> "+$formatted"
            transaction.type == TransactionType.EXPENSE -> "−$formatted"
            else -> formatted
        },
        categoryId = category?.id?.value.orEmpty(),
        categoryIconKey = category?.iconKey.orEmpty(),
        categoryHue = category?.hue ?: CategoryAppearance.UNSET_HUE,
        categorySystemKind = category?.systemKind?.name,
    )
}

private fun pickDefaultCategory(
    categories: List<Category>,
    counts: Map<CategoryId, Int>,
    type: CategoryType,
): Category? = categories
    .filter { it.type == type }
    .maxByOrNull { counts[it.id] ?: 0 }

private fun buildDisplayCategories(
    categories: List<Category>,
    counts: Map<CategoryId, Int>,
    type: CategoryType,
    selected: Category?,
): List<Category> {
    val byUsage = categories
        .filter { it.type == type }
        .sortedByDescending { counts[it.id] ?: 0 }
    val top = byUsage.take(CATEGORY_PREVIEW_SIZE)
    val resolvedSelected = selected?.takeIf { it.type == type }
    return if (resolvedSelected == null || top.any { it.id == resolvedSelected.id }) {
        top
    } else {
        top.take(CATEGORY_PREVIEW_SIZE - 1) + resolvedSelected
    }
}

private const val CATEGORY_PREVIEW_SIZE = 7

/**
 * The existing transaction the creation screen opens on, and what it is there for.
 *
 * One nullable parameter rather than an id plus a flag: the screen loads exactly one transaction
 * in either mode, and two independent parameters could contradict each other (a duplicate flag
 * with no id, an id with no mode).
 */
/**
 * Everything a stored transaction carries that this screen has no field for.
 *
 * `saveTransaction` builds a whole new [Transaction] instead of patching the stored row, so a
 * field that is not routed back through here is silently reset to its default on update — which
 * is how editing used to wipe a transaction's merchant and tags, downgrade a `PLANNED` row to
 * `ACTUAL`, and orphan the other leg of a transfer by dropping its `transferId`.
 *
 * The defaults are what a brand-new transaction should have, so a blank form needs no special case.
 */
data class PreservedTransactionFields(
    val merchant: String = "",
    val tags: List<String> = emptyList(),
    val status: TransactionStatus = TransactionStatus.ACTUAL,
    val transferId: TransferId? = null,
    val recurringRuleId: RecurringRuleId? = null,
) {
    companion object {
        /** Everything [transaction] holds outside the form — what an edit must hand back untouched. */
        fun of(transaction: Transaction): PreservedTransactionFields = PreservedTransactionFields(
            merchant = transaction.merchant,
            tags = transaction.tags,
            status = transaction.status,
            transferId = transaction.transferId,
            recurringRuleId = transaction.recurringRuleId,
        )
    }
}

data class TransactionCreationSeed(
    val transactionId: TransactionId,
    val mode: Mode,
) {
    enum class Mode {
        /** Update the transaction in place. */
        Edit,

        /** Use it as a template for a brand-new transaction. */
        Duplicate,
    }
}

enum class TransactionTypeUi { Expense, Income, Transfer }

/**
 * The transaction being edited, as it was stored — what the edit screen's identity band renders so
 * the user can tell which row they opened without scrolling or going back.
 *
 * A snapshot, not a view of the form: it is taken once when the transaction is loaded and never
 * follows the fields the user is editing.
 */
data class TransactionEditIdentity(
    /** Short human-readable id, e.g. `TX-8213`. */
    val reference: String,
    val type: TransactionTypeUi,
    /** The note, falling back to the merchant when there is none; may still be blank. */
    val note: String,
    /** Signed for income and expense, unsigned for a transfer leg. */
    val formattedAmount: String,
    /** The category's stored appearance, fed to the shared bubble resolver. */
    val categoryId: String,
    val categoryIconKey: String,
    val categoryHue: Int,
    val categorySystemKind: String?,
)

@optics
sealed interface TransactionCreationState {
    data object Loading : TransactionCreationState

    @optics
    data class Content(
        val amount: String,
        val note: String,
        val type: TransactionTypeUi,
        val accounts: List<Account>,
        val categories: List<Category>,
        val selectedAccount: Account?,
        val selectedCategory: Category?,
        val isEditMode: Boolean,
        val editingTransactionId: TransactionId?,
        /** Non-null only in edit mode: the stored row behind the identity band. */
        val editIdentity: TransactionEditIdentity? = null,
        /** Whether the delete confirmation is up — the same dialog the details screen shows. */
        val showDeleteConfirmation: Boolean = false,
        val editingCreatedAt: kotlin.time.Instant? = null,
        // Original `operationDate` from the persisted transaction. Preserved across
        // edits unless the user explicitly picks a new date — otherwise a timezone
        // change between the original save and the edit would silently shift the
        // stored business date.
        val pinnedOperationDate: LocalDate? = null,
        /**
         * Fields of the transaction being edited that this form cannot change but must not
         * destroy — see [PreservedTransactionFields].
         */
        val preserved: PreservedTransactionFields = PreservedTransactionFields(),
        val timestamp: Long,
        val categoryUsageCounts: Map<CategoryId, Int>,
        val displayCategories: List<Category>,
        val fromAccount: Account? = null,
        val toAccount: Account? = null,
        val toAmount: String = "",
        val transferEnabled: Boolean = true,
    ) : TransactionCreationState {
        val isExpense: Boolean get() = type == TransactionTypeUi.Expense
        val isTransfer: Boolean get() = type == TransactionTypeUi.Transfer
        val crossCurrency: Boolean
            get() = isTransfer &&
                fromAccount != null &&
                toAccount != null &&
                fromAccount.currencyCode != toAccount.currencyCode

        /** Inline validation error for the (from) amount field, or null when it is acceptable. */
        val amountError: TransactionAmountError?
            get() = TransactionAmountInput.errorFor(amount)

        /** Inline validation error for the cross-currency "to" amount, or null when it is acceptable. */
        val toAmountError: TransactionAmountError?
            get() = if (crossCurrency) TransactionAmountInput.errorFor(toAmount) else null

        val isSaveEnabled: Boolean
            get() = if (isTransfer) {
                TransactionAmountInput.isValid(amount) &&
                    (!crossCurrency || TransactionAmountInput.isValid(toAmount)) &&
                    fromAccount != null && toAccount != null &&
                    fromAccount.id != toAccount.id
            } else {
                TransactionAmountInput.isValid(amount) &&
                    selectedAccount != null &&
                    selectedCategory != null
            }

        companion object
    }

    companion object
}

sealed interface TransactionCreationEvent {
    data class OnAmountChanged(val amount: String) : TransactionCreationEvent
    data class OnToAmountChanged(val amount: String) : TransactionCreationEvent
    data class OnNoteChanged(val note: String) : TransactionCreationEvent
    data class OnAccountSelected(val account: Account) : TransactionCreationEvent
    data class OnCategorySelected(val category: Category) : TransactionCreationEvent
    data class OnCategoryPicked(val id: CategoryId) : TransactionCreationEvent
    data class OnAccountPicked(val id: AccountId) : TransactionCreationEvent
    data class OnTypeChanged(val type: TransactionTypeUi) : TransactionCreationEvent
    data class OnDateChanged(val timestamp: Long) : TransactionCreationEvent
    data object OnTodayClick : TransactionCreationEvent
    data object OnOpenCategoryChooser : TransactionCreationEvent
    data object OnOpenCategoryCreation : TransactionCreationEvent
    data object OnOpenAccountChooser : TransactionCreationEvent
    data object OnOpenFromAccountChooser : TransactionCreationEvent
    data object OnOpenToAccountChooser : TransactionCreationEvent
    data object OnSwapAccountsClick : TransactionCreationEvent
    data object OnSaveClick : TransactionCreationEvent
    data object OnDeleteClick : TransactionCreationEvent
    data object OnDeleteConfirmed : TransactionCreationEvent
    data object OnDeleteDismissed : TransactionCreationEvent
    data object OnBackClick : TransactionCreationEvent
}

sealed interface TransactionCreationEffect {
    data object NavigateBack : TransactionCreationEffect

    /**
     * The edited transaction is gone, so the screen that opened this one — the details of that very
     * row — must not be returned to. Distinct from [NavigateBack] because only the caller knows how
     * far back that is.
     */
    data object NavigateBackAfterDelete : TransactionCreationEffect
    data class NavigateToCategoryChooser(
        val selectedCategoryId: CategoryId?,
        val filterType: CategoryType,
    ) : TransactionCreationEffect
    data object NavigateToCategoryCreation : TransactionCreationEffect
    data class NavigateToAccountChooser(
        val selectedAccountId: AccountId?,
        val excludeAccountId: AccountId? = null,
        val showTransferShortcut: Boolean = false,
    ) : TransactionCreationEffect
}

internal enum class AccountSlot { Single, From, To }
