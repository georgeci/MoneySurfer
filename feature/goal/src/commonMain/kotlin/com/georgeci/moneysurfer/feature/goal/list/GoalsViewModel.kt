package com.georgeci.moneysurfer.feature.goal.list

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class GoalsViewModel(
    private val getGoals: GetGoalsUseCase,
) : MviViewModel<AsyncState<GoalsContent>, GoalsEvent, GoalsEffect>(
    initialState = AsyncState.Loading,
) {

    init {
        observeGoals()
    }

    override fun onEvent(event: GoalsEvent) {
        when (event) {
            GoalsEvent.OnBackClick -> postSideEffect(GoalsEffect.NavigateBack)
            GoalsEvent.OnAddGoalClick -> postSideEffect(GoalsEffect.NavigateToGoalCreation)
            is GoalsEvent.OnGoalClick -> postSideEffect(GoalsEffect.NavigateToGoalDetails(event.goalId))
        }
    }

    private fun observeGoals() {
        launch {
            getGoals().collect { summaries ->
                val items = summaries.map { it.toUi() }
                updateState { AsyncState.Content(GoalsContent(goals = items)) }
            }
        }
    }

    private fun SavingsGoalSummary.toUi() = GoalListItem(
        id = goal.id,
        title = goal.title,
        emoji = goal.emoji,
        hue = goal.hue,
        status = goal.status,
        targetDate = goal.targetDate,
        progress = progress.percent.toFloat(),
        percentLabel = "${(progress.percent * PERCENT_SCALE).toInt()}%",
        savedLabel = MoneyFormatter.format(progress.saved, goal.currencyCode),
        targetLabel = MoneyFormatter.format(progress.target, goal.currencyCode),
    )

    private companion object {
        const val PERCENT_SCALE = 100
    }
}

data class GoalsContent(
    val goals: List<GoalListItem>,
)

data class GoalListItem(
    val id: GoalId,
    val title: String,
    val emoji: String,
    val hue: Int,
    val status: GoalStatus,
    val targetDate: LocalDate?,
    val progress: Float,
    val percentLabel: String,
    val savedLabel: String,
    val targetLabel: String,
)

sealed interface GoalsEvent {
    data object OnBackClick : GoalsEvent
    data object OnAddGoalClick : GoalsEvent
    data class OnGoalClick(val goalId: GoalId) : GoalsEvent
}

sealed interface GoalsEffect {
    data object NavigateBack : GoalsEffect
    data object NavigateToGoalCreation : GoalsEffect
    data class NavigateToGoalDetails(val goalId: GoalId) : GoalsEffect
}
