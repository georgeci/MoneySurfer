package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import org.koin.core.annotation.Single

/**
 * Archives or restores a budget. The row stays in the database — an archived budget drops
 * out of the main list but keeps its history, so restoring it brings the numbers back.
 */
@Single
class ArchiveBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {

    suspend operator fun invoke(id: BudgetId, isActive: Boolean) {
        budgetRepository.setActive(id, isActive)
    }
}
