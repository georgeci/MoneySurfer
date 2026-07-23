package com.georgeci.moneysurfer.feature.budget.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.usecase.ArchiveBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.feature.budget.BudgetUi
import com.georgeci.moneysurfer.feature.budget.formatShortDate
import com.georgeci.moneysurfer.feature.budget.toUi
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetDetailsViewModel(
    private val budgetId: BudgetId,
    private val budgetRepository: BudgetRepository,
    private val getBudgetProgress: GetBudgetProgressUseCase,
    private val getBudgetTransactions: GetBudgetTransactionsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val archiveBudget: ArchiveBudgetUseCase,
) : MviViewModel<BudgetDetailsState, BudgetDetailsEvent, BudgetDetailsEffect>(
    initialState = BudgetDetailsState.Loading,
) {

    init {
        observeBudget()
    }

    override fun onEvent(event: BudgetDetailsEvent) {
        when (event) {
            BudgetDetailsEvent.OnBackClick -> postSideEffect(BudgetDetailsEffect.NavigateBack)
            BudgetDetailsEvent.OnEditClick -> postSideEffect(BudgetDetailsEffect.NavigateToBudgetEdit(budgetId))
            BudgetDetailsEvent.OnArchiveClick -> toggleArchive()
            is BudgetDetailsEvent.OnTransactionClick ->
                postSideEffect(BudgetDetailsEffect.NavigateToTransactionDetails(event.transactionId))
        }
    }

    private fun toggleArchive() {
        val content = currentState as? BudgetDetailsState.Content ?: return
        launch { archiveBudget(budgetId, isActive = !content.budget.isActive) }
    }

    /**
     * The budget itself is read once and then re-read through the progress stream: progress
     * recomputes on every transaction change, and an edit to the budget lands as a new emission
     * of the repository flow feeding it.
     */
    private fun observeBudget() {
        launch {
            budgetRepository.getAll()
                .flatMapLatest { budgets ->
                    val budget = budgets.firstOrNull { it.id == budgetId }
                        ?: return@flatMapLatest flowOf(null)
                    combine(
                        getBudgetProgress(budget),
                        getBudgetTransactions(budget),
                        getCategories(),
                    ) { progress, transactions, categories ->
                        val categoriesById = categories.associateBy { it.id.value }
                        BudgetDetailsState.Content(
                            budget = progress.toUi(categoriesById),
                            transactions = transactions.map { it.toUi() },
                        )
                    }
                }
                .collect { content ->
                    updateState { content ?: BudgetDetailsState.Missing }
                }
        }
    }

    private fun CategorizedTransaction.toUi() = BudgetTransactionUi(
        id = transaction.id,
        title = transaction.merchant.takeIf { it.isNotBlank() }
            ?: transaction.note.takeIf { it.isNotBlank() }
            ?: categoryName.orEmpty(),
        categoryName = categoryName,
        amountFormatted = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode),
        dateLabel = formatShortDate(transaction.operationDate),
    )
}

@optics
sealed interface BudgetDetailsState {
    data object Loading : BudgetDetailsState

    /** The budget was deleted — on another device, or from the list behind this screen. */
    data object Missing : BudgetDetailsState

    @optics
    data class Content(
        val budget: BudgetUi,
        val transactions: List<BudgetTransactionUi>,
    ) : BudgetDetailsState {
        companion object
    }

    companion object
}

data class BudgetTransactionUi(
    val id: TransactionId,
    val title: String,
    val categoryName: String?,
    val amountFormatted: String,
    val dateLabel: String,
)

sealed interface BudgetDetailsEvent {
    data object OnBackClick : BudgetDetailsEvent
    data object OnEditClick : BudgetDetailsEvent
    data object OnArchiveClick : BudgetDetailsEvent
    data class OnTransactionClick(val transactionId: TransactionId) : BudgetDetailsEvent
}

sealed interface BudgetDetailsEffect {
    data object NavigateBack : BudgetDetailsEffect
    data class NavigateToBudgetEdit(val budgetId: BudgetId) : BudgetDetailsEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : BudgetDetailsEffect
}
