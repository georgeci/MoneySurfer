package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.domain.model.MissingDayPolicy
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.RecurringRuleRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import org.koin.core.annotation.Single

@Single(binds = [RecurringRuleRepository::class])
class RecurringRuleRepositoryImpl(
    private val dao: RecurringRuleDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : RecurringRuleRepository {

    override fun getAll(): Flow<List<RecurringRule>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<RecurringRule>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: RecurringRuleId): RecurringRule? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(rule: RecurringRule) {
        val entity = rule.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(rule: RecurringRule) {
        val existingCreatedAt = dao.getById(rule.id.value)?.createdAt
        val entity = rule.toEntity().let { mapped ->
            mapped.copy(
                createdAt = existingCreatedAt ?: mapped.createdAt,
                updatedAt = clock.now().toEpochMilliseconds(),
            )
        }
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: RecurringRuleId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.RECURRING_RULE,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    private suspend fun enqueueUpsert(entity: RecurringRuleEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.RECURRING_RULE,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun RecurringRuleEntity.toDomain() = RecurringRule(
        id = RecurringRuleId(id),
        workspaceId = WorkspaceId(workspaceId),
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
        startDate = timeFormatter.parseLocalDate(startDate),
        nextRunAt = timeFormatter.parseInstantOrNull(nextRunAt),
        autoCreate = autoCreate,
        isActive = isActive,
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun RecurringRule.toEntity() = RecurringRuleEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        title = title,
        amount = amount.minor,
        categoryId = categoryId.value,
        scheduleFrequency = schedule.frequency.name,
        scheduleInterval = schedule.interval,
        scheduleDaysOfWeek = schedule.daysOfWeek.toWeekdaysStorageValue(),
        scheduleDaysOfMonth = schedule.daysOfMonth.toMonthdaysStorageValue(),
        scheduleMissingDayPolicy = schedule.missingDayPolicy.name,
        startDate = timeFormatter.formatLocalDate(startDate),
        nextRunAt = timeFormatter.formatInstantOrNull(nextRunAt),
        autoCreate = autoCreate,
        isActive = isActive,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}

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
