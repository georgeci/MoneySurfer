package com.georgeci.moneysurfer.domain.usecase

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository

/**
 * Shape rules shared by [CreateSavingsGoalUseCase] and [UpdateSavingsGoalUseCase]:
 * a positive target, and a currency that is the workspace base currency (v1 supports
 * no other — decision 6 in md/goals.md).
 */
internal suspend fun Raise<GoalActionError>.ensureGoalIsValid(
    goal: SavingsGoal,
    workspaceRepository: WorkspaceRepository,
) {
    ensure(goal.target.isPositive()) { GoalActionError.InvalidTarget }
    val workspace = workspaceRepository.getById(goal.workspaceId)
        ?: raise(GoalActionError.WorkspaceNotFound)
    ensure(goal.currencyCode == workspace.baseCurrency) { GoalActionError.CurrencyMismatch }
}
