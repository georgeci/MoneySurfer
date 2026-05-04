package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAll(): Flow<List<Budget>>
    suspend fun getById(id: BudgetId): Budget?
    suspend fun insert(budget: Budget)
    suspend fun update(budget: Budget)
    suspend fun delete(id: BudgetId)
}
