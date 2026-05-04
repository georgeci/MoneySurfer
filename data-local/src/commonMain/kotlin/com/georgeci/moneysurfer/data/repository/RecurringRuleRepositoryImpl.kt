package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.domain.model.MissingDayPolicy
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.repositories.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [RecurringRuleRepository::class])
class RecurringRuleRepositoryImpl(
    private val dao: RecurringRuleDao,
) : RecurringRuleRepository {

    override fun getAll(): Flow<List<RecurringRule>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: RecurringRuleId): RecurringRule? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(rule: RecurringRule) {
        dao.insert(rule.toEntity())
    }

    override suspend fun update(rule: RecurringRule) {
        dao.update(rule.toEntity())
    }

    override suspend fun delete(id: RecurringRuleId) {
        dao.delete(id.value)
    }
}

private fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = RecurringRuleId(id),
    title = title,
    amount = Money.fromMinor(amount),
    categoryId = CategoryId(categoryId),
    schedule = RecurringSchedule(
        frequency = RecurringFrequency.entries.firstOrNull { it.name == scheduleFrequency }
            ?: RecurringFrequency.MONTHLY,
        interval = scheduleInterval,
        daysOfWeek = scheduleDaysOfWeek.parseDaysOfWeek(),
        daysOfMonth = scheduleDaysOfMonth.parseDaysOfMonth(),
        missingDayPolicy = MissingDayPolicy.entries.firstOrNull { it.name == scheduleMissingDayPolicy }
            ?: MissingDayPolicy.LAST_DAY_OF_MONTH,
    ),
    startDate = LocalDate.parse(startDate),
    nextRunAt = nextRunAt?.let { Instant.fromEpochMilliseconds(it) },
    autoCreate = autoCreate,
    isActive = isActive,
)

private fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id.value,
    title = title,
    amount = amount.minor,
    categoryId = categoryId.value,
    scheduleFrequency = schedule.frequency.name,
    scheduleInterval = schedule.interval,
    scheduleDaysOfWeek = schedule.daysOfWeek.toWeekdaysStorageValue(),
    scheduleDaysOfMonth = schedule.daysOfMonth.toMonthdaysStorageValue(),
    scheduleMissingDayPolicy = schedule.missingDayPolicy.name,
    startDate = startDate.toString(),
    nextRunAt = nextRunAt?.toEpochMilliseconds(),
    autoCreate = autoCreate,
    isActive = isActive,
)

private fun String.parseDaysOfWeek(): Set<DayOfWeek> {
    if (isBlank()) return emptySet()
    return split(',').mapNotNull { value ->
        DayOfWeek.entries.firstOrNull { day -> day.name == value }
    }.toSet()
}

private fun String.parseDaysOfMonth(): Set<Int> {
    if (isBlank()) return emptySet()
    return split(',').mapNotNull { it.toIntOrNull() }.toSet()
}

private fun Set<DayOfWeek>.toWeekdaysStorageValue(): String =
    joinToString(",") { it.name }

private fun Set<Int>.toMonthdaysStorageValue(): String =
    joinToString(",") { it.toString() }
