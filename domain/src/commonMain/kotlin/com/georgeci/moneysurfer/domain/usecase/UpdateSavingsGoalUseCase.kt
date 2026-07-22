package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import org.koin.core.annotation.Single

/**
 * Edits a goal. Lowering the target below the saved amount completes the goal, and
 * raising it above re-opens it — the same rule [AddContributionUseCase] applies from
 * the other side, since either edge can be crossed by moving either number.
 */
@Single
class UpdateSavingsGoalUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    suspend operator fun invoke(goal: SavingsGoal): Either<GoalActionError, SavingsGoal> = either {
        goalRepository.getById(goal.id) ?: raise(GoalActionError.GoalNotFound)
        ensureGoalIsValid(goal, workspaceRepository)
        val saved = contributionRepository.savedAmount(goal.id)
        val retargeted = goal.copy(status = goal.status.afterProgressChange(saved >= goal.target))
        Either.catch { goalRepository.update(retargeted) }
            .mapLeft { GoalActionError.LocalWriteFailed(it) }
            .bind()
        retargeted
    }
}

/**
 * The ACTIVE ↔ COMPLETED flip. Only those two statuses move: a PAUSED or ARCHIVED
 * goal keeps its status no matter what the numbers do.
 */
internal fun GoalStatus.afterProgressChange(isReached: Boolean): GoalStatus = when {
    this == GoalStatus.ACTIVE && isReached -> GoalStatus.COMPLETED
    this == GoalStatus.COMPLETED && !isReached -> GoalStatus.ACTIVE
    else -> this
}
