package com.georgeci.moneysurfer.feature.goal.details

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoalDetails
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.usecase.DeleteSavingsGoalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalDetailsUseCase
import com.georgeci.moneysurfer.domain.usecase.SetGoalStatusUseCase
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_details_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class GoalDetailsViewModel(
    private val goalId: GoalId,
    private val getGoalDetails: GetGoalDetailsUseCase,
    private val setGoalStatus: SetGoalStatusUseCase,
    private val deleteGoal: DeleteSavingsGoalUseCase,
    private val accountRepository: AccountRepository,
    private val snackbar: SnackbarController,
) : MviViewModel<AsyncState<GoalDetailsContent>, GoalDetailsEvent, GoalDetailsEffect>(
    initialState = AsyncState.Loading,
) {

    init {
        observeGoal()
    }

    override fun onEvent(event: GoalDetailsEvent) {
        when (event) {
            GoalDetailsEvent.OnBackClick -> postSideEffect(GoalDetailsEffect.NavigateBack)
            GoalDetailsEvent.OnEditClick -> postSideEffect(GoalDetailsEffect.NavigateToEdit(goalId))
            GoalDetailsEvent.OnAddMoneyClick -> postSideEffect(
                GoalDetailsEffect.NavigateToContribution(goalId, GoalContributionMode.ADD),
            )

            GoalDetailsEvent.OnWithdrawClick -> postSideEffect(
                GoalDetailsEffect.NavigateToContribution(goalId, GoalContributionMode.WITHDRAW),
            )

            GoalDetailsEvent.OnActionsClick -> setActionsSheet(visible = true)
            GoalDetailsEvent.OnActionsDismiss -> setActionsSheet(visible = false)
            GoalDetailsEvent.OnPauseResumeClick -> togglePause()
            GoalDetailsEvent.OnArchiveClick -> toggleArchive()
            GoalDetailsEvent.OnDeleteClick -> setDeleteDialog(visible = true)
            GoalDetailsEvent.OnDeleteCancel -> setDeleteDialog(visible = false)
            GoalDetailsEvent.OnDeleteConfirm -> confirmDelete()
        }
    }

    private fun observeGoal() {
        launch {
            getGoalDetails(goalId).collect { details ->
                if (details == null) {
                    // The goal is gone — deleted here or pulled away by a sync from another device.
                    postSideEffect(GoalDetailsEffect.NavigateBack)
                    return@collect
                }
                val accountName = details.goal.accountId?.let { accountName(it) }
                updateState {
                    val content = details.toUi(accountName)
                    when (this) {
                        is AsyncState.Content -> copy(value = content, pending = false)
                        AsyncState.Loading -> AsyncState.Content(content)
                    }
                }
            }
        }
    }

    private suspend fun accountName(id: AccountId): String? = accountRepository.getById(id)?.name

    private fun setActionsSheet(visible: Boolean) = updateContent { copy(showActions = visible) }

    private fun setDeleteDialog(visible: Boolean) =
        updateContent { copy(showDeleteConfirmation = visible, showActions = false) }

    private fun togglePause() {
        val content = contentOrNull() ?: return
        val next = if (content.status == GoalStatus.PAUSED) GoalStatus.ACTIVE else GoalStatus.PAUSED
        applyStatus(next)
    }

    private fun toggleArchive() {
        val content = contentOrNull() ?: return
        val next = if (content.status == GoalStatus.ARCHIVED) GoalStatus.ACTIVE else GoalStatus.ARCHIVED
        applyStatus(next)
    }

    private fun applyStatus(status: GoalStatus) {
        setActionsSheet(visible = false)
        launch {
            setGoalStatus(goalId, status)
        }
    }

    private fun confirmDelete() {
        setDeleteDialog(visible = false)
        val title = contentOrNull()?.title
        launch {
            deleteGoal(goalId) ?: return@launch
            if (title != null) {
                snackbar.show(Res.string.goal_details_deleted_snackbar, listOf(title))
            }
            postSideEffect(GoalDetailsEffect.NavigateBack)
        }
    }

    private fun contentOrNull(): GoalDetailsContent? = (currentState as? AsyncState.Content)?.value

    private fun updateContent(reducer: GoalDetailsContent.() -> GoalDetailsContent) = updateState {
        when (this) {
            is AsyncState.Content -> copy(value = value.reducer())
            AsyncState.Loading -> this
        }
    }

    private fun SavingsGoalDetails.toUi(accountName: String?) = GoalDetailsContent(
        title = goal.title,
        emoji = goal.emoji,
        hue = goal.hue,
        status = goal.status,
        targetDate = goal.targetDate,
        accountName = accountName,
        progress = progress.percent.toFloat(),
        percentLabel = "${(progress.percent * PERCENT_SCALE).toInt()}%",
        savedLabel = MoneyFormatter.format(progress.saved, goal.currencyCode),
        targetLabel = MoneyFormatter.format(progress.target, goal.currencyCode),
        remainingLabel = MoneyFormatter.format(progress.remaining, goal.currencyCode),
        isReached = progress.isReached,
        contributions = contributions.map { it.toUi(goal.currencyCode) },
    )

    /** Signed for the eye, not for the maths: the sign lives in the amount itself. */
    private fun GoalContribution.toUi(currency: CurrencyCode) = GoalContributionUi(
        id = id,
        amountLabel = MoneyFormatter.format(amount.abs(), currency)
            .let { if (amount.isNegative()) "−$it" else "+$it" },
        occurredOn = occurredOn,
        note = note,
        isWithdrawal = amount.isNegative(),
    )

    private companion object {
        const val PERCENT_SCALE = 100
    }
}

data class GoalDetailsContent(
    val title: String,
    val emoji: String,
    val hue: Int,
    val status: GoalStatus,
    val targetDate: LocalDate?,
    val accountName: String?,
    val progress: Float,
    val percentLabel: String,
    val savedLabel: String,
    val targetLabel: String,
    val remainingLabel: String,
    val isReached: Boolean,
    val contributions: List<GoalContributionUi>,
    val showActions: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
)

data class GoalContributionUi(
    val id: GoalContributionId,
    val amountLabel: String,
    val occurredOn: LocalDate,
    val note: String,
    val isWithdrawal: Boolean,
)

sealed interface GoalDetailsEvent {
    data object OnBackClick : GoalDetailsEvent
    data object OnEditClick : GoalDetailsEvent
    data object OnAddMoneyClick : GoalDetailsEvent
    data object OnWithdrawClick : GoalDetailsEvent
    data object OnActionsClick : GoalDetailsEvent
    data object OnActionsDismiss : GoalDetailsEvent
    data object OnPauseResumeClick : GoalDetailsEvent
    data object OnArchiveClick : GoalDetailsEvent
    data object OnDeleteClick : GoalDetailsEvent
    data object OnDeleteConfirm : GoalDetailsEvent
    data object OnDeleteCancel : GoalDetailsEvent
}

sealed interface GoalDetailsEffect {
    data object NavigateBack : GoalDetailsEffect
    data class NavigateToEdit(val goalId: GoalId) : GoalDetailsEffect
    data class NavigateToContribution(val goalId: GoalId, val mode: GoalContributionMode) : GoalDetailsEffect
}
