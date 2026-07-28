package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate

/** A monthly rule by default — the shape a subscription actually takes. */
fun aRecurringRule(
    id: RecurringRuleId = recurringRuleId(),
    workspaceId: WorkspaceId = workspaceId(),
    title: String = "Streaming",
    amount: Money = 10.dollars,
    categoryId: CategoryId = categoryId(),
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    interval: Int = 1,
    startDate: LocalDate = LocalDate(2026, 1, 1),
    isActive: Boolean = true,
): RecurringRule = RecurringRule(
    id = id,
    workspaceId = workspaceId,
    title = title,
    amount = amount,
    categoryId = categoryId,
    schedule = RecurringSchedule(frequency = frequency, interval = interval),
    startDate = startDate,
    nextRunAt = null,
    isActive = isActive,
    createdAt = testInstant,
)
