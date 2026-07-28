package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate

/**
 * A monthly rule by default — the shape a subscription actually takes.
 *
 * Title and start date are fixed rather than parameters: no spec varies them, and SonarCloud's
 * `kotlin:S107` caps a signature at seven. Add one back when a test actually needs it.
 */
fun aRecurringRule(
    id: RecurringRuleId = recurringRuleId(),
    workspaceId: WorkspaceId = workspaceId(),
    amount: Money = 10.dollars,
    categoryId: CategoryId = categoryId(),
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    interval: Int = 1,
    isActive: Boolean = true,
): RecurringRule = RecurringRule(
    id = id,
    workspaceId = workspaceId,
    title = "Streaming",
    amount = amount,
    categoryId = categoryId,
    schedule = RecurringSchedule(frequency = frequency, interval = interval),
    startDate = LocalDate(2026, 1, 1),
    nextRunAt = null,
    isActive = isActive,
    createdAt = testInstant,
)
