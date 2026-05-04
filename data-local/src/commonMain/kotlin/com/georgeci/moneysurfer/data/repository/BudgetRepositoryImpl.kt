package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [BudgetRepository::class])
class BudgetRepositoryImpl(
    private val dao: BudgetDao,
) : BudgetRepository {

    override fun getAll(): Flow<List<Budget>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: BudgetId): Budget? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(budget: Budget) {
        dao.insert(budget.toEntity())
    }

    override suspend fun update(budget: Budget) {
        dao.update(budget.toEntity())
    }

    override suspend fun delete(id: BudgetId) {
        dao.delete(id.value)
    }
}

private fun BudgetEntity.toDomain() = Budget(
    id = BudgetId(id),
    name = name,
    categoryIds = categoryIds.parseCategoryIds(),
    amount = Money.fromMinor(amount),
    period = BudgetPeriod.entries.firstOrNull { it.name == period } ?: BudgetPeriod.MONTHLY,
    startDate = startDate.toLocalDate(),
    alertPercent = alertPercent,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

private fun Budget.toEntity() = BudgetEntity(
    id = id.value,
    name = name,
    categoryIds = categoryIds.toStorageValue(),
    amount = amount.minor,
    period = period.name,
    startDate = startDate.toIsoDate(),
    alertPercent = alertPercent,
    isActive = isActive,
    createdAt = createdAt.toEpochMillis(),
    updatedAt = updatedAt.toEpochMillis(),
)

private fun String.parseCategoryIds(): List<CategoryId> {
    if (isBlank()) return emptyList()
    return split(',').filter { it.isNotBlank() }.map(::CategoryId)
}

private fun List<CategoryId>.toStorageValue(): String =
    joinToString(",") { categoryId -> categoryId.value }
