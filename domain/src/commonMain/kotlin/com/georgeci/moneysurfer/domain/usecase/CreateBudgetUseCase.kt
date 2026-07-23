package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import org.koin.core.annotation.Single

@Single
class CreateBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {

    suspend operator fun invoke(budget: Budget) {
        budgetRepository.insert(budget)
    }
}
