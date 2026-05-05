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
    private val timeFormatter: TimeFormatter,
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

    private fun BudgetEntity.toDomain() = Budget(
        id = BudgetId(id),
        name = name,
        categoryIds = categoryIds.parseCategoryIds(),
        amount = Money.fromMinor(amount),
        period = BudgetPeriod.entries.firstOrNull { it.name == period } ?: BudgetPeriod.MONTHLY,
        startDate = timeFormatter.parseLocalDate(startDate),
        alertPercent = alertPercent,
        isActive = isActive,
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun Budget.toEntity() = BudgetEntity(
        id = id.value,
        name = name,
        categoryIds = categoryIds.toStorageValue(),
        amount = amount.minor,
        period = period.name,
        startDate = timeFormatter.formatLocalDate(startDate),
        alertPercent = alertPercent,
        isActive = isActive,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}

private fun String.parseCategoryIds(): List<CategoryId> {
    if (isBlank()) return emptyList()
    return split(',').filter { it.isNotBlank() }.map(::CategoryId)
}

private fun List<CategoryId>.toStorageValue(): String =
    joinToString(",") { categoryId -> categoryId.value }
