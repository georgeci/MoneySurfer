package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface SavingsGoalRepository {
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<SavingsGoal>>
    suspend fun getById(id: GoalId): SavingsGoal?
    suspend fun insert(goal: SavingsGoal)
    suspend fun update(goal: SavingsGoal)

    /**
     * Hard-deletes the goal and, through Room's cascade, its contributions.
     * Contribution deletes are enqueued on the outbox explicitly — Firestore has
     * no cascade, so silently dropped local rows would leave remote orphans.
     */
    suspend fun delete(id: GoalId)
}
