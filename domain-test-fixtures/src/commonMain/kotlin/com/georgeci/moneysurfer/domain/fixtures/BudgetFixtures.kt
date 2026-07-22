package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Suppress("LongParameterList")
fun aBudget(
    id: BudgetId = budgetId(),
    workspaceId: WorkspaceId = workspaceId(),
    name: String = "Groceries",
    categoryIds: List<CategoryId> = listOf(categoryId()),
    amount: Money = 500.dollars,
    period: BudgetPeriod = BudgetPeriod.MONTHLY,
    startDate: LocalDate = testDate,
    alertPercent: Int = 80,
    isActive: Boolean = true,
    rollover: Boolean = false,
    createdAt: Instant = testInstant,
    updatedAt: Instant = createdAt,
): Budget = Budget(
    id = id,
    workspaceId = workspaceId,
    name = name,
    categoryIds = categoryIds,
    amount = amount,
    period = period,
    startDate = startDate,
    alertPercent = alertPercent,
    isActive = isActive,
    rollover = rollover,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
