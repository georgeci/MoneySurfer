package com.georgeci.moneysurfer.feature.budget.edit

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.usecase.CreateBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.EditBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.feature.budget.BudgetCategoryUi
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budget_edit_created_snackbar
import moneysurfer.feature.budget.generated.resources.budget_edit_updated_snackbar
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BudgetEditViewModel(
    private val budgetId: BudgetId?,
    private val budgetRepository: BudgetRepository,
    private val createBudget: CreateBudgetUseCase,
    private val editBudget: EditBudgetUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<BudgetEditState, BudgetEditEvent, BudgetEditEffect>(
    initialState = BudgetEditState(),
) {

    init {
        updateState { copy(startDate = today()) }
        if (budgetId != null) loadBudget(budgetId)
        observeCategories()
    }

    override fun onEvent(event: BudgetEditEvent) {
        when (event) {
            is BudgetEditEvent.OnNameChanged -> updateState {
                copy(name = event.name.take(BudgetEditState.NAME_MAX_LENGTH), nameTouched = true)
            }
            is BudgetEditEvent.OnAmountChanged -> updateState { copy(amount = event.amount, amountTouched = true) }
            is BudgetEditEvent.OnPeriodChanged -> updateState { copy(period = event.period) }
            is BudgetEditEvent.OnStartDateChanged -> updateState { copy(startDate = event.date) }
            is BudgetEditEvent.OnCategoryToggled -> toggleCategory(event.categoryId)
            BudgetEditEvent.OnAllCategoriesSelected -> updateState { copy(selectedCategoryIds = emptySet()) }
            is BudgetEditEvent.OnAlertPercentChanged -> updateState { copy(alertPercent = event.percent) }
            is BudgetEditEvent.OnRolloverChanged -> updateState { copy(rollover = event.enabled) }
            BudgetEditEvent.OnBackClick -> postSideEffect(BudgetEditEffect.NavigateBack)
            BudgetEditEvent.OnSaveClick -> save()
        }
    }

    private fun toggleCategory(categoryId: String) = updateState {
        copy(
            selectedCategoryIds = if (categoryId in selectedCategoryIds) {
                selectedCategoryIds - categoryId
            } else {
                selectedCategoryIds + categoryId
            },
        )
    }

    private fun loadBudget(id: BudgetId) {
        launch {
            updateState { copy(isLoading = true) }
            val budget = budgetRepository.getById(id)
            if (budget == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            updateState {
                copy(
                    name = budget.name,
                    amount = budget.amount.toInput(),
                    period = budget.period,
                    startDate = budget.startDate,
                    selectedCategoryIds = budget.categoryIds.map { it.value }.toSet(),
                    alertPercent = budget.alertPercent,
                    rollover = budget.rollover,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Expense categories only: a budget caps spending, so offering income categories would let
     * the user build one that can never move.
     */
    private fun observeCategories() {
        launch {
            getCategories().collect { categories ->
                val options = categories
                    .filter { it.type == CategoryType.EXPENSE }
                    .map {
                        BudgetCategoryUi(
                            id = it.id.value,
                            name = it.name,
                            iconKey = it.iconKey,
                            hue = it.hue,
                            systemKind = it.systemKind?.name,
                        )
                    }
                updateState {
                    copy(
                        categoryOptions = options,
                        // Drop a selection whose category was deleted rather than saving a dangling id.
                        selectedCategoryIds = selectedCategoryIds.filter { selected ->
                            options.any { it.id == selected }
                        }.toSet(),
                    )
                }
            }
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        val amount = state.amountAsMoney ?: return
        launch {
            updateState { copy(isLoading = true) }
            val trimmedName = state.name.trim()
            val categoryIds = state.selectedCategoryIds.map(::CategoryId)

            if (budgetId != null) {
                val existing = budgetRepository.getById(budgetId)
                if (existing != null) {
                    editBudget(
                        existing.copy(
                            name = trimmedName,
                            amount = amount,
                            period = state.period,
                            startDate = state.startDate,
                            categoryIds = categoryIds,
                            alertPercent = state.alertPercent,
                            rollover = state.rollover,
                        ),
                    )
                    snackbar.show(Res.string.budget_edit_updated_snackbar, listOf(trimmedName))
                }
                postSideEffect(BudgetEditEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.flow.first()
            if (workspaceId == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            createBudget(
                Budget(
                    id = BudgetId.uuid(),
                    workspaceId = workspaceId,
                    name = trimmedName,
                    categoryIds = categoryIds,
                    amount = amount,
                    period = state.period,
                    startDate = state.startDate,
                    alertPercent = state.alertPercent,
                    rollover = state.rollover,
                    createdAt = getCurrentTime(),
                ),
            )
            snackbar.show(Res.string.budget_edit_created_snackbar, listOf(trimmedName))
            postSideEffect(BudgetEditEffect.NavigateBack)
        }
    }

    private fun today(): LocalDate =
        clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

private const val MINOR_PER_MAJOR = 100
private const val DECIMALS = 2

/** Money → the text the amount field shows, in major units with two decimals. */
private fun Money.toInput(): String {
    val major = minor / MINOR_PER_MAJOR
    val cents = (minor % MINOR_PER_MAJOR).toInt()
    return if (cents == 0) major.toString() else "$major.${cents.toString().padStart(DECIMALS, '0')}"
}

data class BudgetEditState(
    val name: String = "",
    /** Raw field text, already normalized by `AmountInputTransformation`. */
    val amount: String = "",
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: LocalDate = LocalDate(EPOCH_YEAR, 1, 1),
    /** Empty means "all categories" — a general budget, exactly as the domain reads it. */
    val selectedCategoryIds: Set<String> = emptySet(),
    val categoryOptions: List<BudgetCategoryUi> = emptyList(),
    val alertPercent: Int = DEFAULT_ALERT_PERCENT,
    val rollover: Boolean = false,
    val isLoading: Boolean = false,
    val nameTouched: Boolean = false,
    val amountTouched: Boolean = false,
) {
    val amountAsMoney: Money?
        get() = amount.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let(Money::fromDouble)

    val canSave: Boolean get() = name.isNotBlank() && amountAsMoney != null && !isLoading

    val nameMissing: Boolean get() = nameTouched && name.isBlank()

    val amountMissing: Boolean get() = amountTouched && amountAsMoney == null

    companion object {
        const val NAME_MAX_LENGTH = 50
        const val DEFAULT_ALERT_PERCENT = 80

        /** Placeholder only — the view model overwrites it with today on init. */
        const val EPOCH_YEAR = 1970
    }
}

sealed interface BudgetEditEvent {
    data class OnNameChanged(val name: String) : BudgetEditEvent
    data class OnAmountChanged(val amount: String) : BudgetEditEvent
    data class OnPeriodChanged(val period: BudgetPeriod) : BudgetEditEvent
    data class OnStartDateChanged(val date: LocalDate) : BudgetEditEvent
    data class OnCategoryToggled(val categoryId: String) : BudgetEditEvent
    data object OnAllCategoriesSelected : BudgetEditEvent
    data class OnAlertPercentChanged(val percent: Int) : BudgetEditEvent
    data class OnRolloverChanged(val enabled: Boolean) : BudgetEditEvent
    data object OnBackClick : BudgetEditEvent
    data object OnSaveClick : BudgetEditEvent
}

sealed interface BudgetEditEffect {
    data object NavigateBack : BudgetEditEffect
}
