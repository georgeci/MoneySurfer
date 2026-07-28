package com.georgeci.moneysurfer.feature.goal.edit

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.CreateSavingsGoalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GoalActionError
import com.georgeci.moneysurfer.domain.usecase.UpdateSavingsGoalUseCase
import com.georgeci.moneysurfer.feature.goal.GoalAmountInput
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalEmojis
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_edit_created_snackbar
import moneysurfer.feature.goal.generated.resources.goal_edit_error_currency
import moneysurfer.feature.goal.generated.resources.goal_edit_error_generic
import moneysurfer.feature.goal.generated.resources.goal_edit_target_error
import moneysurfer.feature.goal.generated.resources.goal_edit_updated_snackbar
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel

/**
 * Create and edit share one screen: [goalId] is `null` for a new goal, exactly like the
 * category editor.
 */
@KoinViewModel
class GoalEditViewModel(
    private val goalId: GoalId?,
    private val createGoal: CreateSavingsGoalUseCase,
    private val updateGoal: UpdateSavingsGoalUseCase,
    private val goalRepository: SavingsGoalRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<GoalEditState, GoalEditEvent, GoalEditEffect>(
    initialState = GoalEditState(isEdit = goalId != null),
) {

    init {
        if (goalId != null) loadGoal(goalId)
    }

    override fun onEvent(event: GoalEditEvent) {
        when (event) {
            is GoalEditEvent.OnTitleChanged -> updateState {
                copy(title = event.title.take(TITLE_MAX_LENGTH), titleTouched = true)
            }

            is GoalEditEvent.OnEmojiSelected -> updateState { copy(emoji = event.emoji) }
            is GoalEditEvent.OnHueSelected -> updateState { copy(hue = event.hue) }
            is GoalEditEvent.OnTargetChanged -> updateState {
                copy(target = GoalAmountInput.sanitize(event.target), targetTouched = true)
            }

            is GoalEditEvent.OnTargetDateChanged -> updateState { copy(targetDate = event.date) }
            GoalEditEvent.OnTargetDateCleared -> updateState { copy(targetDate = null) }
            GoalEditEvent.OnDatePickerOpen -> updateState { copy(showDatePicker = true) }
            GoalEditEvent.OnDatePickerDismiss -> updateState { copy(showDatePicker = false) }
            GoalEditEvent.OnBackClick -> postSideEffect(GoalEditEffect.NavigateBack)
            GoalEditEvent.OnSaveClick -> save()
        }
    }

    private fun loadGoal(id: GoalId) {
        launch {
            val goal = goalRepository.getById(id) ?: return@launch
            updateState {
                copy(
                    title = goal.title,
                    emoji = goal.emoji,
                    hue = goal.hue,
                    target = (goal.target.minor / MINOR_PER_MAJOR.toDouble()).toString(),
                    targetDate = goal.targetDate,
                )
            }
        }
    }

    private fun save() {
        val state = currentState
        val target = GoalAmountInput.parse(state.target) ?: return
        if (state.title.isBlank()) return
        launch {
            updateState { copy(inFlight = true, error = null) }
            val title = state.title.trim()
            val existing = goalId?.let { goalRepository.getById(it) }
            if (existing != null) {
                updateGoal(
                    existing.copy(
                        title = title,
                        emoji = state.emoji,
                        hue = state.hue,
                        target = target,
                        targetDate = state.targetDate,
                    ),
                ).fold(
                    ifLeft = ::fail,
                    ifRight = {
                        snackbar.show(Res.string.goal_edit_updated_snackbar, listOf(title))
                        finish()
                    },
                )
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.first()
            val workspace = workspaceId?.let { workspaceRepository.getById(it) }
            if (workspace == null) {
                fail(GoalActionError.WorkspaceNotFound)
                return@launch
            }
            val now = getCurrentTime()
            createGoal(
                SavingsGoal(
                    workspaceId = workspace.id,
                    title = title,
                    emoji = state.emoji,
                    hue = state.hue,
                    target = target,
                    currencyCode = workspace.baseCurrency,
                    startDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    targetDate = state.targetDate,
                    createdAt = now,
                ),
            ).fold(
                ifLeft = ::fail,
                ifRight = {
                    snackbar.show(Res.string.goal_edit_created_snackbar, listOf(title))
                    finish()
                },
            )
        }
    }

    private fun finish() {
        updateState { copy(inFlight = false) }
        postSideEffect(GoalEditEffect.NavigateBack)
    }

    /** Stay on the screen with the user's input intact — navigating back would lose it. */
    private fun fail(error: GoalActionError) {
        updateState { copy(inFlight = false, error = error.messageRes()) }
    }

    private fun GoalActionError.messageRes(): StringResource = when (this) {
        GoalActionError.InvalidTarget -> Res.string.goal_edit_target_error
        GoalActionError.CurrencyMismatch -> Res.string.goal_edit_error_currency
        else -> Res.string.goal_edit_error_generic
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 50
        const val MINOR_PER_MAJOR = 100
    }
}

data class GoalEditState(
    val isEdit: Boolean = false,
    val title: String = "",
    val emoji: String = SurferGoalEmojis.All.first(),
    val hue: Int = SurferGoalEmojis.Hues.first(),
    val target: String = "",
    val targetDate: LocalDate? = null,
    val showDatePicker: Boolean = false,
    val inFlight: Boolean = false,
    val error: StringResource? = null,
    val titleTouched: Boolean = false,
    val targetTouched: Boolean = false,
) {
    val titleMissing: Boolean get() = titleTouched && title.isBlank()
    val targetMissing: Boolean get() = targetTouched && GoalAmountInput.parse(target) == null
    val canSave: Boolean
        get() = title.isNotBlank() && GoalAmountInput.parse(target) != null && !inFlight
}

sealed interface GoalEditEvent {
    data class OnTitleChanged(val title: String) : GoalEditEvent
    data class OnEmojiSelected(val emoji: String) : GoalEditEvent
    data class OnHueSelected(val hue: Int) : GoalEditEvent
    data class OnTargetChanged(val target: String) : GoalEditEvent
    data class OnTargetDateChanged(val date: LocalDate) : GoalEditEvent
    data object OnTargetDateCleared : GoalEditEvent
    data object OnDatePickerOpen : GoalEditEvent
    data object OnDatePickerDismiss : GoalEditEvent
    data object OnBackClick : GoalEditEvent
    data object OnSaveClick : GoalEditEvent
}

sealed interface GoalEditEffect {
    data object NavigateBack : GoalEditEffect
}
