package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import org.koin.core.annotation.Single

/**
 * Pause, resume and archive. COMPLETED is not settable — it is derived from progress,
 * so resuming a goal that is already past its target lands on COMPLETED instead of
 * ACTIVE.
 */
@Single
class SetGoalStatusUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
) {

    suspend operator fun invoke(
        id: GoalId,
        status: GoalStatus,
    ): Either<GoalActionError, GoalStatus> = either {
        ensure(status != GoalStatus.COMPLETED) { GoalActionError.StatusNotSettable }
        val goal = goalRepository.getById(id) ?: raise(GoalActionError.GoalNotFound)
        val saved = contributionRepository.savedAmount(id)
        val resolved = status.afterProgressChange(saved >= goal.target)
        if (resolved == goal.status) return@either resolved
        Either.catch { goalRepository.update(goal.copy(status = resolved)) }
            .mapLeft { GoalActionError.LocalWriteFailed(it) }
            .bind()
        resolved
    }
}
