package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface GoalContributionRepository {
    /** Newest first. */
    fun getByGoalId(goalId: GoalId): Flow<List<GoalContribution>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<GoalContribution>>

    /**
     * `SUM(amount)` for the goal — the only source of a goal's `current` amount.
     * Zero when the goal has no contributions.
     */
    fun observeSavedAmount(goalId: GoalId): Flow<Money>
    suspend fun savedAmount(goalId: GoalId): Money

    suspend fun getById(id: GoalContributionId): GoalContribution?
    suspend fun insert(contribution: GoalContribution)
    suspend fun update(contribution: GoalContribution)
    suspend fun delete(id: GoalContributionId)
}
