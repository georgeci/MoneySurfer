package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import org.koin.core.annotation.Single

/**
 * Hard-deletes a goal and its contributions. Archiving is the reversible option —
 * this one is for "remove it for good".
 */
@Single
class DeleteSavingsGoalUseCase(
    private val goalRepository: SavingsGoalRepository,
) {

    /** Returns the removed goal, or `null` when there was nothing to remove. */
    suspend operator fun invoke(id: GoalId): SavingsGoal? {
        val existing = goalRepository.getById(id) ?: return null
        goalRepository.delete(id)
        return existing
    }
}
