package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class Budget(
    val id: BudgetId = BudgetId.uuid(),
    val workspaceId: WorkspaceId,
    val name: String,
    /** Empty = every expense category. Income categories are never counted. */
    val categoryIds: List<CategoryId>,
    val amount: Money,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: LocalDate,
    val alertPercent: Int = 80,
    val isActive: Boolean = true,
    /** Carry the previous period's unspent remainder into the next period's limit. */
    val rollover: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)

enum class BudgetPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY,
}
