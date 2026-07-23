package com.georgeci.moneysurfer.feature.budget

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.BudgetId

/** Sample data for `@Preview`s only — never referenced from a screen at runtime. */
@Suppress("LongParameterList")
internal fun previewBudget(
    id: String = "b-1",
    name: String = "Groceries",
    status: BudgetStatus = BudgetStatus.WARN,
    spent: String = "€312.40",
    limit: String = "€400.00",
    remainder: String = "€87.60",
    progress: Float = 0.78f,
    isActive: Boolean = true,
): BudgetUi = BudgetUi(
    id = BudgetId(id),
    name = name,
    status = status,
    spentFormatted = spent,
    limitFormatted = limit,
    remainderFormatted = remainder,
    isOver = status == BudgetStatus.OVER,
    progress = progress,
    alertFraction = 0.8f,
    alertPercent = 80,
    percent = (progress * 100).toInt(),
    period = BudgetPeriod.MONTHLY,
    windowLabel = "1 Apr – 30 Apr",
    daysLeft = 12,
    elapsedDays = 18,
    categories = emptyList(),
    isActive = isActive,
    hasMixedCurrency = false,
    rolloverCarryFormatted = null,
    dailyAverageFormatted = "€17.35",
    projectedTotalFormatted = "€520.50",
    perDayRemainingFormatted = "€7.30",
    overspendFormatted = "€0.00",
)
