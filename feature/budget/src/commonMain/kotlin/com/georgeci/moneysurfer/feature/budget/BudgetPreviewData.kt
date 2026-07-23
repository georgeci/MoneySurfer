package com.georgeci.moneysurfer.feature.budget

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.BudgetId

/**
 * Sample data for `@Preview`s only — never referenced from a screen at runtime. Takes only the
 * fields the previews actually vary; everything else is fixed here.
 */
internal fun previewBudget(
    name: String = "Groceries",
    status: BudgetStatus = BudgetStatus.WARN,
    progress: Float = 0.78f,
): BudgetUi = BudgetUi(
    id = BudgetId("b-1"),
    name = name,
    status = status,
    spentFormatted = "€312.40",
    limitFormatted = "€400.00",
    remainderFormatted = "€87.60",
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
    isActive = true,
    hasMixedCurrency = false,
    rolloverCarryFormatted = null,
    dailyAverageFormatted = "€17.35",
    projectedTotalFormatted = "€520.50",
    perDayRemainingFormatted = "€7.30",
    overspendFormatted = "€0.00",
)
