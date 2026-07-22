package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Reactive in-memory stand-ins for the goal repositories, backed by [MutableStateFlow]
 * so `combine` callers see every write without manual re-emission.
 */
class FakeSavingsGoalRepository(seed: List<SavingsGoal> = emptyList()) : SavingsGoalRepository {
    private val state = MutableStateFlow(seed)

    val rows: List<SavingsGoal> get() = state.value

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<SavingsGoal>> =
        state.map { rows -> rows.filter { it.workspaceId == workspaceId } }

    override suspend fun getById(id: GoalId): SavingsGoal? = state.value.firstOrNull { it.id == id }

    override fun observeById(id: GoalId): Flow<SavingsGoal?> =
        state.map { rows -> rows.firstOrNull { it.id == id } }

    override suspend fun insert(goal: SavingsGoal) {
        state.value = state.value + goal
    }

    override suspend fun update(goal: SavingsGoal) {
        state.value = state.value.map { if (it.id == goal.id) goal else it }
    }

    override suspend fun delete(id: GoalId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeGoalContributionRepository(
    seed: List<GoalContribution> = emptyList(),
) : GoalContributionRepository {
    private val state = MutableStateFlow(seed)

    val rows: List<GoalContribution> get() = state.value

    override fun getByGoalId(goalId: GoalId): Flow<List<GoalContribution>> =
        state.map { rows -> rows.filter { it.goalId == goalId }.sortedByDescending { it.occurredOn } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<GoalContribution>> =
        state.map { rows -> rows.filter { it.workspaceId == workspaceId } }

    override fun observeSavedAmount(goalId: GoalId): Flow<Money> =
        state.map { rows -> rows.sumFor(goalId) }

    override suspend fun savedAmount(goalId: GoalId): Money = state.value.sumFor(goalId)

    override suspend fun getById(id: GoalContributionId): GoalContribution? =
        state.value.firstOrNull { it.id == id }

    override suspend fun insert(contribution: GoalContribution) {
        state.value = state.value + contribution
    }

    override suspend fun update(contribution: GoalContribution) {
        state.value = state.value.map { if (it.id == contribution.id) contribution else it }
    }

    override suspend fun delete(id: GoalContributionId) {
        state.value = state.value.filterNot { it.id == id }
    }

    private fun List<GoalContribution>.sumFor(goalId: GoalId): Money =
        filter { it.goalId == goalId }.fold(Money.zero()) { acc, row -> acc + row.amount }
}

class FakeGoalWorkspaceRepository(seed: List<Workspace> = emptyList()) : WorkspaceRepository {
    private val state = MutableStateFlow(seed)

    override fun getAll(): Flow<List<Workspace>> = state
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> =
        state.map { rows -> rows.filter { it.ownerId == userId } }

    override suspend fun getById(id: WorkspaceId): Workspace? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(workspace: Workspace) {
        state.value = state.value + workspace
    }

    override suspend fun update(workspace: Workspace) {
        state.value = state.value.map { if (it.id == workspace.id) workspace else it }
    }

    override suspend fun delete(id: WorkspaceId) {
        state.value = state.value.filterNot { it.id == id }
    }
}
