package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.GoalProgress
import com.georgeci.moneysurfer.domain.model.SavingsGoalDetails
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single

/**
 * One goal with its progress and full contribution history. Emits `null` once the goal
 * is gone, so the details screen can close itself after a delete.
 */
@Single
class GetGoalDetailsUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
) {

    operator fun invoke(goalId: GoalId): Flow<SavingsGoalDetails?> =
        combine(
            goalRepository.observeById(goalId),
            contributionRepository.getByGoalId(goalId),
        ) { goal, contributions ->
            goal ?: return@combine null
            val saved = contributions.fold(Money.zero()) { acc, row -> acc + row.amount }
            SavingsGoalDetails(
                goal = goal,
                progress = GoalProgress.of(goal, saved),
                contributions = contributions,
            )
        }
}
