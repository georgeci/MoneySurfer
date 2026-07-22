package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import org.koin.core.annotation.Single

@Single
class CreateSavingsGoalUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    suspend operator fun invoke(goal: SavingsGoal): Either<GoalActionError, SavingsGoal> = either {
        ensureGoalIsValid(goal, workspaceRepository)
        Either.catch { goalRepository.insert(goal) }
            .mapLeft { GoalActionError.LocalWriteFailed(it) }
            .bind()
        goal
    }
}
