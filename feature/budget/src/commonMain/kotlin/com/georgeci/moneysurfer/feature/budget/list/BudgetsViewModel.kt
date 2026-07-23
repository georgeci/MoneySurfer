package com.georgeci.moneysurfer.feature.budget.list

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.usecase.ArchiveBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.feature.budget.BudgetUi
import com.georgeci.moneysurfer.feature.budget.toUi
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budgets_archive_undo
import moneysurfer.feature.budget.generated.resources.budgets_archived_snackbar
import moneysurfer.feature.budget.generated.resources.budgets_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val getBudgets: GetBudgetsUseCase,
    private val getBudgetProgress: GetBudgetProgressUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val archiveBudget: ArchiveBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
    private val session: SessionPointers,
    private val snackbar: SnackbarController,
) : MviViewModel<BudgetsState, BudgetsEvent, BudgetsEffect>(initialState = BudgetsState.Loading) {

    init {
        observeBudgets()
    }

    override fun onEvent(event: BudgetsEvent) {
        when (event) {
            BudgetsEvent.OnBackClick -> postSideEffect(BudgetsEffect.NavigateBack)
            BudgetsEvent.OnAddBudgetClick -> postSideEffect(BudgetsEffect.NavigateToBudgetCreation)
            is BudgetsEvent.OnBudgetClick -> postSideEffect(BudgetsEffect.NavigateToBudgetDetails(event.budgetId))
            is BudgetsEvent.OnArchiveClick -> setActive(event.budgetId, isActive = false)
            is BudgetsEvent.OnRestoreClick -> setActive(event.budgetId, isActive = true)
            is BudgetsEvent.OnDeleteClick -> requestDelete(event.budgetId)
            BudgetsEvent.OnDeleteCancel -> dismissDelete()
            BudgetsEvent.OnDeleteConfirm -> confirmDelete()
        }
    }

    private fun setActive(budgetId: BudgetId, isActive: Boolean) {
        val name = nameOf(budgetId)
        launch {
            archiveBudget(budgetId, isActive)
            if (!isActive && name != null) {
                snackbar.show(
                    message = Res.string.budgets_archived_snackbar,
                    messageArgs = listOf(name),
                    actionLabel = Res.string.budgets_archive_undo,
                    onAction = { archiveBudget(budgetId, isActive = true) },
                )
            }
        }
    }

    private fun requestDelete(budgetId: BudgetId) {
        val name = nameOf(budgetId) ?: return
        updateState {
            BudgetsState.content.pendingDelete.modify(this) { BudgetsPendingDelete(budgetId, name) }
        }
    }

    private fun dismissDelete() = updateState {
        BudgetsState.content.pendingDelete.modify(this) { null }
    }

    private fun confirmDelete() {
        val target = (currentState as? BudgetsState.Content)?.pendingDelete ?: return
        updateState { BudgetsState.content.pendingDelete.modify(this) { null } }
        launch {
            deleteBudget(target.id) ?: return@launch
            snackbar.show(Res.string.budgets_deleted_snackbar, listOf(target.name))
        }
    }

    private fun nameOf(budgetId: BudgetId): String? {
        val content = currentState as? BudgetsState.Content ?: return null
        return (content.active + content.archived).firstOrNull { it.id == budgetId }?.name
    }

    /**
     * One subscription for every card: budgets and categories drive a single progress query, so
     * adding a budget costs a recomposition rather than another transaction read per card.
     */
    private fun observeBudgets() {
        launch {
            session.currentWorkspaceId.flow
                .flatMapLatest { workspaceId ->
                    if (workspaceId == null) {
                        flowOf(emptyList())
                    } else {
                        combine(getBudgets(), getCategories()) { budgets, categories -> budgets to categories }
                            .flatMapLatest { (budgets, categories) ->
                                val categoriesById = categories.associateBy { it.id.value }
                                getBudgetProgress.progressOf(workspaceId, budgets)
                                    .map { progresses -> progresses.map { it.toUi(categoriesById) } }
                            }
                    }
                }
                .collect { budgets ->
                    updateState {
                        when (this) {
                            is BudgetsState.Loading -> BudgetsState.Content(
                                active = budgets.filter { it.isActive },
                                archived = budgets.filterNot { it.isActive },
                            )
                            is BudgetsState.Content -> copy(
                                active = budgets.filter { it.isActive },
                                archived = budgets.filterNot { it.isActive },
                            )
                        }
                    }
                }
        }
    }
}

@optics
sealed interface BudgetsState {
    data object Loading : BudgetsState

    @optics
    data class Content(
        val active: List<BudgetUi>,
        val archived: List<BudgetUi>,
        val pendingDelete: BudgetsPendingDelete? = null,
    ) : BudgetsState {
        companion object
    }

    companion object
}

data class BudgetsPendingDelete(
    val id: BudgetId,
    val name: String,
)

sealed interface BudgetsEvent {
    data object OnBackClick : BudgetsEvent
    data object OnAddBudgetClick : BudgetsEvent
    data class OnBudgetClick(val budgetId: BudgetId) : BudgetsEvent
    data class OnArchiveClick(val budgetId: BudgetId) : BudgetsEvent
    data class OnRestoreClick(val budgetId: BudgetId) : BudgetsEvent
    data class OnDeleteClick(val budgetId: BudgetId) : BudgetsEvent
    data object OnDeleteConfirm : BudgetsEvent
    data object OnDeleteCancel : BudgetsEvent
}

sealed interface BudgetsEffect {
    data object NavigateBack : BudgetsEffect
    data object NavigateToBudgetCreation : BudgetsEffect
    data class NavigateToBudgetDetails(val budgetId: BudgetId) : BudgetsEffect
}
