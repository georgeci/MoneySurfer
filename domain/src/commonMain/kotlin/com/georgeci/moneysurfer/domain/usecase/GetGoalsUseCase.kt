package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.GoalProgress
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.model.SavingsGoalSummary
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

/**
 * Goals of the current workspace with their saved amounts folded in. Contributions are
 * read once per workspace rather than once per goal — the list would otherwise open a
 * `SUM` flow per row.
 *
 * ARCHIVED goals are hidden; v1 has no list filter to bring them back (md/goals.md).
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetGoalsUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(includeArchived: Boolean = false): Flow<List<SavingsGoalSummary>> =
        session.currentWorkspaceId.flow.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(emptyList())
            combine(
                goalRepository.getByWorkspaceId(workspaceId),
                contributionRepository.getByWorkspaceId(workspaceId),
            ) { goals, contributions ->
                val savedByGoal = contributions
                    .groupBy { it.goalId }
                    .mapValues { (_, rows) -> rows.fold(Money.zero()) { acc, row -> acc + row.amount } }
                goals
                    .filter { includeArchived || it.status != GoalStatus.ARCHIVED }
                    .sortedWith(goalOrder)
                    .map { goal ->
                        SavingsGoalSummary(
                            goal = goal,
                            progress = GoalProgress.of(goal, savedByGoal[goal.id] ?: Money.zero()),
                        )
                    }
            }
        }
}

/** Goals you are still saving for float to the top; newest first inside a status. */
private val goalOrder = compareBy<SavingsGoal> { goal ->
    when (goal.status) {
        GoalStatus.ACTIVE -> 0
        GoalStatus.COMPLETED -> 1
        GoalStatus.PAUSED -> 2
        GoalStatus.ARCHIVED -> 3
    }
}.thenByDescending { it.createdAt }
