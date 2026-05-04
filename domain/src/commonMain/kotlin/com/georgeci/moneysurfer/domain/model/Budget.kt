package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.LocalDate

data class Budget(
    val id: BudgetId = BudgetId.uuid(),
    val name: String,
    val categoryIds: List<CategoryId>,
    val amount: Money,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: LocalDate,
    val alertPercent: Int = 80,
    val isActive: Boolean = true,
)

enum class BudgetPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY,
}
