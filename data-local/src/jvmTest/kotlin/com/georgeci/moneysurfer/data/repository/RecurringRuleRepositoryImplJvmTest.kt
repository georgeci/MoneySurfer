package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aRecurringRule
import com.georgeci.moneysurfer.domain.fixtures.recurringRuleId
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.model.MissingDayPolicy
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringSchedule
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlin.time.Duration.Companion.hours

/**
 * The schedule is the interesting part: Room stores the weekday and month-day sets as CSV columns
 * and the frequency/policy enums by name, so every read re-parses text that a forward-compatible
 * row is allowed to hold values for that this client does not know.
 */
class RecurringRuleRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var outbox: RecordingOutbox
    lateinit var repository: RecurringRuleRepositoryImpl

    val later = testInstant + 5.hours

    beforeEach {
        database = inMemoryLocalDatabase()
        outbox = RecordingOutbox()
        repository = RecurringRuleRepositoryImpl(
            dao = database.recurringRuleDao(),
            outboxEnqueuer = outbox,
            clock = ClockUseCase(FixedClock(later)),
            timeFormatter = TimeFormatter(),
        )
        database.seedWorkspaceRows()
    }

    afterEach { database.close() }

    "a weekly rule round-trips its weekday set through the CSV column" {
        val rule = aRecurringRule(frequency = RecurringFrequency.WEEKLY, interval = 2).let {
            it.copy(
                schedule = it.schedule.copy(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            )
        }

        repository.insert(rule)

        repository.getById(rule.id) shouldBe rule
    }

    "a monthly rule round-trips its month-day set and missing-day policy" {
        val rule = aRecurringRule().let {
            it.copy(
                schedule = RecurringSchedule(
                    frequency = RecurringFrequency.MONTHLY,
                    daysOfMonth = setOf(1, 15, 31),
                    missingDayPolicy = MissingDayPolicy.SKIP,
                ),
            )
        }

        repository.insert(rule)

        repository.getById(rule.id) shouldBe rule
    }

    "a rule with no day sets reads back empty rather than as a blank day" {
        repository.insert(aRecurringRule())

        val stored = repository.getById(recurringRuleId())!!
        stored.schedule.daysOfWeek shouldBe emptySet()
        stored.schedule.daysOfMonth shouldBe emptySet()
    }

    // Both enums are stored by name, so a row written by a newer client can carry a value this
    // build has never heard of. Reading it must degrade to the default, not throw mid-list.
    "unknown frequency and policy names degrade to the defaults" {
        repository.insert(aRecurringRule())
        val stored = database.recurringRuleDao().getById(recurringRuleId().value)!!
        database.recurringRuleDao().update(
            stored.copy(
                scheduleFrequency = "FORTNIGHTLY",
                scheduleMissingDayPolicy = "NEAREST_WEEKDAY",
                scheduleDaysOfWeek = "MONDAY,CATURDAY",
                scheduleDaysOfMonth = "1,not-a-day",
            ),
        )

        val rule = repository.getById(recurringRuleId())!!
        rule.schedule.frequency shouldBe RecurringFrequency.MONTHLY
        rule.schedule.missingDayPolicy shouldBe MissingDayPolicy.LAST_DAY_OF_MONTH
        rule.schedule.daysOfWeek shouldBe setOf(DayOfWeek.MONDAY)
        rule.schedule.daysOfMonth shouldBe setOf(1)
    }

    "an insert is paired with an INSERT outbox entry scoped to the workspace" {
        repository.insert(aRecurringRule())

        outbox.records shouldContainExactly listOf(
            OutboxRecord(
                entityType = SyncEntityTypes.RECURRING_RULE,
                entityId = recurringRuleId().value,
                scopeKey = TEST_WORKSPACE_ID,
                operation = MutationOperation.INSERT,
            ),
        )
    }

    "an update keeps the stored createdAt and stamps updatedAt from the clock" {
        repository.insert(aRecurringRule())

        repository.update(aRecurringRule(isActive = false))

        val updated = repository.getById(recurringRuleId())!!
        updated.isActive shouldBe false
        updated.createdAt shouldBe testInstant
        updated.updatedAt shouldBe later
    }

    "a delete drops the row and enqueues a tombstone" {
        repository.insert(aRecurringRule())
        outbox.records.clear()

        repository.delete(recurringRuleId())

        repository.getById(recurringRuleId()).shouldBeNull()
        outbox.records.map { it.operation } shouldContainExactly listOf(null)
    }

    "deleting a rule that is already gone enqueues nothing" {
        repository.delete(recurringRuleId("missing"))

        outbox.records shouldContainExactly emptyList()
    }

    "both streams see the stored rules" {
        repository.insert(aRecurringRule(id = recurringRuleId("r-1")))
        repository.insert(aRecurringRule(id = recurringRuleId("r-2")))

        repository.getAll().first().size shouldBe 2
        repository.getByWorkspaceId(aRecurringRule().workspaceId).first().size shouldBe 2
    }
})
