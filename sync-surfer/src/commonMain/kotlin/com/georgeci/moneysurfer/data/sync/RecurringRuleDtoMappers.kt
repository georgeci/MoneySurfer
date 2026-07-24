package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.data.remote.RecurringRuleDoc
import com.georgeci.moneysurfer.domain.model.MissingDayPolicy
import com.georgeci.moneysurfer.domain.model.RecurringFrequency

/**
 * Recurring-rule Room ↔ Firestore-DTO mappers. Same contract as the entity mappers in
 * `SyncDtoMappers.kt`, kept in their own file because that one is at its function ceiling.
 *
 * The schedule day sets are CSV columns in Room and real lists on the wire — the same
 * conversion the budget mappers make for `categoryIds`. Blank segments are dropped so a
 * legacy `""` or a trailing comma decodes to an empty list rather than a blank day name.
 */
fun RecurringRuleEntity.toDoc(): RecurringRuleDoc = RecurringRuleDoc(
    title = title,
    amount = amount,
    categoryId = categoryId,
    scheduleFrequency = scheduleFrequency,
    scheduleInterval = scheduleInterval,
    scheduleDaysOfWeek = scheduleDaysOfWeek.split(',').filter { it.isNotBlank() },
    scheduleDaysOfMonth = scheduleDaysOfMonth.split(',').mapNotNull { it.trim().toIntOrNull() },
    scheduleMissingDayPolicy = scheduleMissingDayPolicy,
    startDate = startDate,
    nextRunAt = nextRunAt,
    autoCreate = autoCreate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecurringRuleDoc.toEntity(id: String, workspaceId: String): RecurringRuleEntity =
    RecurringRuleEntity(
        id = id,
        workspaceId = workspaceId,
        title = title,
        amount = amount,
        categoryId = categoryId,
        // A legacy/partial doc may omit the enum names; blank stores an unparseable value,
        // so fall back to the same defaults RecurringRuleRepositoryImpl parses to.
        scheduleFrequency = scheduleFrequency.ifBlank { RecurringFrequency.MONTHLY.name },
        scheduleInterval = scheduleInterval,
        scheduleDaysOfWeek = scheduleDaysOfWeek.filter { it.isNotBlank() }.joinToString(","),
        scheduleDaysOfMonth = scheduleDaysOfMonth.joinToString(","),
        scheduleMissingDayPolicy = scheduleMissingDayPolicy.ifBlank {
            MissingDayPolicy.LAST_DAY_OF_MONTH.name
        },
        startDate = startDate,
        nextRunAt = nextRunAt,
        autoCreate = autoCreate,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
