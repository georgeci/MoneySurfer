package com.georgeci.moneysurfer.feature.transaction.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class TransactionCreationViewModel(
    private val transactionId: TransactionId?,
    private val accountId: AccountId?,
    private val getAccounts: GetAccountsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getTransactionById: GetTransactionByIdUseCase,
    private val createTransaction: CreateTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val transactionRepository: TransactionRepository,
) : MviViewModel<TransactionCreationState, TransactionCreationEvent, TransactionCreationEffect>(
    initialState = TransactionCreationState.Loading,
) {

    init {
        loadData()
        observeCategoryUsage()
    }

    override fun onEvent(event: TransactionCreationEvent) {
        when (event) {
            is TransactionCreationEvent.OnAmountChanged ->
                updateState { TransactionCreationState.content.amount.modify(this) { event.amount } }
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
            TransactionCreationEvent.OnOpenCategoryChooser -> {
                val state = currentState as? TransactionCreationState.Content ?: return
                postSideEffect(
                    TransactionCreationEffect.NavigateToCategoryChooser(
                        selectedCategoryId = state.selectedCategory?.id,
                        filterType = if (state.type == TransactionTypeUi.Income) {
                            CategoryType.INCOME
                        } else {
                            CategoryType.EXPENSE
                        },
                    ),
                )
            }
            TransactionCreationEvent.OnOpenCategoryCreation -> postSideEffect(
                TransactionCreationEffect.NavigateToCategoryCreation,
            )
            TransactionCreationEvent.OnOpenAccountChooser -> {
                val state = currentState as? TransactionCreationState.Content ?: return
                postSideEffect(TransactionCreationEffect.NavigateToAccountChooser(state.selectedAccount?.id))
            }
            TransactionCreationEvent.OnSaveClick -> saveTransaction()
            TransactionCreationEvent.OnBackClick -> postSideEffect(TransactionCreationEffect.NavigateBack)
        }
    }

    private fun changeType(nextType: TransactionTypeUi) = updateState {
        val content = this as? TransactionCreationState.Content ?: return@updateState this
        val nextCategoryType = if (nextType == TransactionTypeUi.Income) {
            CategoryType.INCOME
        } else {
            CategoryType.EXPENSE
        }
        val nextSelected = pickDefaultCategory(content.categories, content.categoryUsageCounts, nextCategoryType)
        content.copy(
            type = nextType,
            selectedCategory = nextSelected,
            displayCategories = buildDisplayCategories(
                categories = content.categories,
                counts = content.categoryUsageCounts,
                type = nextCategoryType,
                selected = nextSelected,
            ),
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
            )

            if (transactionId != null) {
                val transaction = getTransactionById(transactionId)
                if (transaction == null) {
                    updateState { baseContent }
                    return@launch
                }
                val account = accounts.find { it.id == transaction.accountId }
                val category = categories.find { it.id == transaction.categoryId }
                val amountDouble = transaction.money.minor / Money.MINOR_PER_MAJOR
                val amountText = if (amountDouble == amountDouble.toLong().toDouble()) {
                    amountDouble.toLong().toString()
                } else {
                    amountDouble.toString()
                }
                val resolvedType = when (transaction.type) {
                    TransactionType.INCOME -> TransactionTypeUi.Income
                    else -> TransactionTypeUi.Expense
                }
                val activeCategoryType = if (resolvedType == TransactionTypeUi.Income) {
                    CategoryType.INCOME
                } else {
                    CategoryType.EXPENSE
                }
                val resolvedSelected = category ?: initialSelected
                updateState {
                    baseContent.copy(
                        amount = amountText,
                        note = transaction.note,
                        type = resolvedType,
                        selectedAccount = account ?: baseContent.selectedAccount,
                        selectedCategory = resolvedSelected,
                        timestamp = transaction.operationAt.toEpochMilliseconds(),
                        isEditMode = true,
                        editingTransactionId = transactionId,
                        editingCreatedAt = transaction.createdAt,
                        pinnedOperationDate = transaction.operationDate,
                        displayCategories = buildDisplayCategories(
                            categories = categories,
                            counts = baseContent.categoryUsageCounts,
                            type = activeCategoryType,
                            selected = resolvedSelected,
                        ),
                    )
                }
            } else {
                updateState { baseContent }
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
        val match = content.accounts.find { it.id == picked }
        if (match != null) {
            updateState { TransactionCreationState.content.selectedAccount.modify(this) { match } }
            return
        }
        launch {
            val refreshed = getAccounts().first()
            val newMatch = refreshed.find { it.id == picked }
            updateState {
                val c = this as? TransactionCreationState.Content ?: return@updateState this
                c.copy(
                    accounts = refreshed,
                    selectedAccount = newMatch ?: c.selectedAccount,
                )
            }
        }
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
        if (state.type == TransactionTypeUi.Transfer) return
        val account = state.selectedAccount ?: return
        val category = state.selectedCategory ?: return
        val amountDouble = state.amount.toDoubleOrNull() ?: return

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
                operationAt = operationAt,
                operationDate = state.pinnedOperationDate
                    ?: operationAt.toLocalDateTime(zone).date,
                type = type,
                createdAt = state.editingCreatedAt ?: now,
                updatedAt = now,
            )

            if (state.isEditMode) {
                updateTransaction(transaction)
            } else {
                createTransaction(transaction)
            }

            postSideEffect(TransactionCreationEffect.NavigateBack)
        }
    }
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

enum class TransactionTypeUi { Expense, Income, Transfer }

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
        val editingCreatedAt: kotlin.time.Instant? = null,
        // Original `operationDate` from the persisted transaction. Preserved across
        // edits unless the user explicitly picks a new date — otherwise a timezone
        // change between the original save and the edit would silently shift the
        // stored business date.
        val pinnedOperationDate: LocalDate? = null,
        val timestamp: Long,
        val categoryUsageCounts: Map<CategoryId, Int>,
        val displayCategories: List<Category>,
    ) : TransactionCreationState {
        val isExpense: Boolean get() = type == TransactionTypeUi.Expense
        val isSaveEnabled: Boolean
            get() = type != TransactionTypeUi.Transfer &&
                amount.toDoubleOrNull() != null &&
                amount.toDoubleOrNull()!! > 0 &&
                selectedAccount != null &&
                selectedCategory != null

        companion object
    }

    companion object
}

sealed interface TransactionCreationEvent {
    data class OnAmountChanged(val amount: String) : TransactionCreationEvent
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
    data object OnSaveClick : TransactionCreationEvent
    data object OnBackClick : TransactionCreationEvent
}

sealed interface TransactionCreationEffect {
    data object NavigateBack : TransactionCreationEffect
    data class NavigateToCategoryChooser(
        val selectedCategoryId: CategoryId?,
        val filterType: CategoryType,
    ) : TransactionCreationEffect
    data object NavigateToCategoryCreation : TransactionCreationEffect
    data class NavigateToAccountChooser(val selectedAccountId: AccountId?) : TransactionCreationEffect
}
