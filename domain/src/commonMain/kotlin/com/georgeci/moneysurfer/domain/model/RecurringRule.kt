package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class RecurringRule(
    val id: RecurringRuleId = RecurringRuleId.uuid(),
    /**
     * Workspace the rule belongs to. Every co-member sees the same rule, so
     * [com.georgeci.moneysurfer.domain.model.Transaction.recurringRuleId] resolves on
     * every device rather than dangling on the ones that never created it.
     */
    val workspaceId: WorkspaceId,
    val title: String,
    val amount: Money,
    val categoryId: CategoryId,
    val schedule: RecurringSchedule,
    val startDate: LocalDate,
    val nextRunAt: Instant?,
    val autoCreate: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    val updatedAt: Instant = createdAt,
)

data class RecurringSchedule(
    val frequency: RecurringFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val daysOfMonth: Set<Int> = emptySet(),
    val missingDayPolicy: MissingDayPolicy = MissingDayPolicy.LAST_DAY_OF_MONTH,
)

enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

enum class MissingDayPolicy {
    SKIP,
    LAST_DAY_OF_MONTH,
}
