package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.LocalDate

fun aBudget(
    id: BudgetId = budgetId(),
    name: String = "Groceries",
    categoryIds: List<CategoryId> = listOf(categoryId()),
    amount: Money = 500.dollars,
    period: BudgetPeriod = BudgetPeriod.MONTHLY,
    startDate: LocalDate = testDate,
    alertPercent: Int = 80,
    isActive: Boolean = true,
): Budget = Budget(
    id = id,
    name = name,
    categoryIds = categoryIds,
    amount = amount,
    period = period,
    startDate = startDate,
    alertPercent = alertPercent,
    isActive = isActive,
)
