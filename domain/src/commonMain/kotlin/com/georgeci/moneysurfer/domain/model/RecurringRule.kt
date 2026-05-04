package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class RecurringRule(
    val id: RecurringRuleId = RecurringRuleId.uuid(),
    val title: String,
    val amount: Money,
    val categoryId: CategoryId,
    val schedule: RecurringSchedule,
    val startDate: LocalDate,
    val nextRunAt: Instant?,
    val autoCreate: Boolean = true,
    val isActive: Boolean = true,
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
