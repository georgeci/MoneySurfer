package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.data.remote.RecurringRuleDoc
import com.georgeci.moneysurfer.domain.model.MissingDayPolicy
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import kotlinx.datetime.DayOfWeek

/**
 * Recurring-rule Room ↔ Firestore-DTO mappers. Same contract as the entity mappers in
 * `SyncDtoMappers.kt`, kept in their own file because that one is at its function ceiling.
 *
 * The schedule day sets are CSV columns in Room and real lists on the wire — the same
 * conversion the budget mappers make for `categoryIds`. Blank segments are dropped so a
 * legacy `""` or a trailing comma decodes to an empty list rather than a blank day name.
 *
 * Both directions normalize the day sets, because a remote doc is untrusted: the Firestore
 * rules gate membership and client version, not field shape, so any co-member's client can
 * write `"MONDAY "` or day `32`. Those survive the wire type check but not
 * [com.georgeci.moneysurfer.data.repository.RecurringRuleRepositoryImpl]'s parse, which
 * matches exact enum names and would silently drop the day — a rule that renders with the
 * wrong cadence rather than failing visibly. Normalizing here keeps the bad segment out of
 * Room in the first place.
 */
fun RecurringRuleEntity.toDoc(): RecurringRuleDoc = RecurringRuleDoc(
    title = title,
    amount = amount,
    categoryId = categoryId,
    scheduleFrequency = scheduleFrequency,
    scheduleInterval = scheduleInterval,
    scheduleDaysOfWeek = scheduleDaysOfWeek.split(',').toDayOfWeekNames(),
    scheduleDaysOfMonth = scheduleDaysOfMonth.split(',').toDaysOfMonth(),
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
        scheduleDaysOfWeek = scheduleDaysOfWeek.toDayOfWeekNames().joinToString(","),
        scheduleDaysOfMonth = scheduleDaysOfMonth
            .filter { it in DAYS_OF_MONTH_RANGE }
            .joinToString(","),
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

/**
 * Trims each segment and keeps only real [kotlinx.datetime.DayOfWeek] names, so a padded
 * `"MONDAY "` normalizes rather than being dropped downstream and a junk name never reaches Room.
 */
private fun List<String>.toDayOfWeekNames(): List<String> =
    mapNotNull { segment ->
        val trimmed = segment.trim()
        DayOfWeek.entries.firstOrNull { it.name == trimmed }?.name
    }

/** Trims each segment and keeps only days that exist in some month. */
private fun List<String>.toDaysOfMonth(): List<Int> =
    mapNotNull { it.trim().toIntOrNull() }.filter { it in DAYS_OF_MONTH_RANGE }

/** Widest calendar month — February is clamped by `MissingDayPolicy`, not by this filter. */
private val DAYS_OF_MONTH_RANGE = 1..31
