package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import org.koin.core.annotation.Single

/**
 * Hard delete. Returns the removed budget so the caller can offer an Undo — the regular
 * "get it out of my way" path is [ArchiveBudgetUseCase], which keeps the row.
 */
@Single
class DeleteBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {

    suspend operator fun invoke(id: BudgetId): Budget? {
        val existing = budgetRepository.getById(id) ?: return null
        budgetRepository.delete(id)
        return existing
    }
}
