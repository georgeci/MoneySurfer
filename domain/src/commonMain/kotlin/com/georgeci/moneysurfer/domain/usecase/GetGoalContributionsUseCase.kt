package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

/** A goal's contribution history, newest first. */
@Single
class GetGoalContributionsUseCase(
    private val contributionRepository: GoalContributionRepository,
) {

    operator fun invoke(goalId: GoalId): Flow<List<GoalContribution>> =
        contributionRepository.getByGoalId(goalId)
}
