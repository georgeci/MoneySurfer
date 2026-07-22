package com.georgeci.moneysurfer.feature.goal.contribution

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.usecase.AddContributionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GoalActionError
import com.georgeci.moneysurfer.domain.usecase.WithdrawFromGoalUseCase
import com.georgeci.moneysurfer.feature.goal.GoalAmountInput
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_contribution_added_snackbar
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_amount
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_below_zero
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_generic
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_paused
import moneysurfer.feature.goal.generated.resources.goal_contribution_withdrawn_snackbar
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel

/**
 * Add money / withdraw. One screen, one view model — [mode] only flips which use case runs
 * and which strings show, because a withdrawal is the same entity with the sign turned around.
 */
@KoinViewModel
class GoalContributionViewModel(
    private val goalId: GoalId,
    private val mode: GoalContributionMode,
    private val addContribution: AddContributionUseCase,
    private val withdrawFromGoal: WithdrawFromGoalUseCase,
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<GoalContributionState, GoalContributionEvent, GoalContributionEffect>(
    initialState = GoalContributionState(mode = mode),
) {

    init {
        loadGoal()
    }

    override fun onEvent(event: GoalContributionEvent) {
        when (event) {
            is GoalContributionEvent.OnAmountChanged -> updateState {
                copy(amount = GoalAmountInput.sanitize(event.amount), error = null)
            }

            is GoalContributionEvent.OnNoteChanged -> updateState { copy(note = event.note) }
            is GoalContributionEvent.OnDateChanged -> updateState { copy(occurredOn = event.date) }
            GoalContributionEvent.OnDatePickerOpen -> updateState { copy(showDatePicker = true) }
            GoalContributionEvent.OnDatePickerDismiss -> updateState { copy(showDatePicker = false) }
            GoalContributionEvent.OnBackClick -> postSideEffect(GoalContributionEffect.NavigateBack)
            GoalContributionEvent.OnSaveClick -> save()
        }
    }

    private fun loadGoal() {
        launch {
            val goal = goalRepository.getById(goalId) ?: return@launch
            val saved = contributionRepository.savedAmount(goalId)
            val today = getCurrentTime().toLocalDateTime(TimeZone.currentSystemDefault()).date
            updateState {
                copy(
                    goalTitle = goal.title,
                    savedLabel = MoneyFormatter.format(saved, goal.currencyCode),
                    occurredOn = today,
                )
            }
        }
    }

    private fun save() {
        val state = currentState
        val amount = GoalAmountInput.parse(state.amount)
        val occurredOn = state.occurredOn
        if (amount == null || occurredOn == null) {
            updateState { copy(error = Res.string.goal_contribution_error_amount) }
            return
        }
        launch {
            updateState { copy(inFlight = true) }
            val result = when (mode) {
                GoalContributionMode.ADD -> addContribution(goalId, amount, occurredOn, state.note.trim())
                GoalContributionMode.WITHDRAW -> withdrawFromGoal(goalId, amount, occurredOn, state.note.trim())
            }
            result.fold(
                ifLeft = { error -> updateState { copy(inFlight = false, error = error.messageRes()) } },
                ifRight = { contribution ->
                    val currency = goalRepository.getById(goalId)?.currencyCode
                    if (currency != null) {
                        snackbar.show(
                            message = when (mode) {
                                GoalContributionMode.ADD -> Res.string.goal_contribution_added_snackbar
                                GoalContributionMode.WITHDRAW -> Res.string.goal_contribution_withdrawn_snackbar
                            },
                            messageArgs = listOf(
                                MoneyFormatter.format(contribution.amount.abs(), currency),
                                state.goalTitle,
                            ),
                        )
                    }
                    postSideEffect(GoalContributionEffect.NavigateBack)
                },
            )
        }
    }

    private fun GoalActionError.messageRes(): StringResource = when (this) {
        GoalActionError.InvalidAmount -> Res.string.goal_contribution_error_amount
        GoalActionError.WithdrawalBelowZero -> Res.string.goal_contribution_error_below_zero
        GoalActionError.GoalNotAcceptingContributions -> Res.string.goal_contribution_error_paused
        else -> Res.string.goal_contribution_error_generic
    }
}

data class GoalContributionState(
    val mode: GoalContributionMode,
    val goalTitle: String = "",
    val savedLabel: String = "",
    val amount: String = "",
    val note: String = "",
    val occurredOn: LocalDate? = null,
    val showDatePicker: Boolean = false,
    val inFlight: Boolean = false,
    val error: StringResource? = null,
) {
    val canSave: Boolean
        get() = GoalAmountInput.parse(amount) != null && occurredOn != null && !inFlight
}

sealed interface GoalContributionEvent {
    data class OnAmountChanged(val amount: String) : GoalContributionEvent
    data class OnNoteChanged(val note: String) : GoalContributionEvent
    data class OnDateChanged(val date: LocalDate) : GoalContributionEvent
    data object OnDatePickerOpen : GoalContributionEvent
    data object OnDatePickerDismiss : GoalContributionEvent
    data object OnBackClick : GoalContributionEvent
    data object OnSaveClick : GoalContributionEvent
}

sealed interface GoalContributionEffect {
    data object NavigateBack : GoalContributionEffect
}
