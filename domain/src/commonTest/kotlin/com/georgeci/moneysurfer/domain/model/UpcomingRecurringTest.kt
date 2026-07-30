package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.recurringRuleId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** 15 April 2026, a Wednesday — mid-month and mid-week, so nothing lands on an edge by accident. */
private val today = LocalDate(2026, 4, 15)

private fun ruleOf(
    id: String = "r-1",
    schedule: RecurringSchedule,
    startDate: LocalDate = LocalDate(2026, 1, 1),
    amount: Money = 10.dollars,
    isActive: Boolean = true,
) = RecurringRule(
    id = recurringRuleId(id),
    workspaceId = workspaceId("ws-1"),
    title = id,
    amount = amount,
    categoryId = categoryId(),
    schedule = schedule,
    startDate = startDate,
    nextRunAt = null,
    isActive = isActive,
)

class UpcomingRecurringTest : StringSpec({

    "a daily rule fires on the day it lands on its own step" {
        val rule = ruleOf(schedule = RecurringSchedule(RecurringFrequency.DAILY, interval = 7))
        // 1 Jan + 105 days = 16 April, and 105 is fifteen whole seven-day steps.
        rule.nextOccurrenceOnOrAfter(LocalDate(2026, 4, 16)) shouldBe LocalDate(2026, 4, 16)
    }

    "a daily rule skips to its next step when today is off it" {
        val rule = ruleOf(schedule = RecurringSchedule(RecurringFrequency.DAILY, interval = 7))
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 16)
    }

    "a rule that has not started yet is dated from its start" {
        val rule = ruleOf(
            schedule = RecurringSchedule(RecurringFrequency.DAILY),
            startDate = LocalDate(2026, 5, 1),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 5, 1)
    }

    "a weekly rule takes the next of its selected weekdays" {
        val rule = ruleOf(
            schedule = RecurringSchedule(
                RecurringFrequency.WEEKLY,
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            ),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 17)
    }

    "a weekly rule with no weekday keeps the one it started on" {
        // 1 January 2026 is a Thursday.
        val rule = ruleOf(schedule = RecurringSchedule(RecurringFrequency.WEEKLY))
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 16)
    }

    "a fortnightly rule skips the off week" {
        val rule = ruleOf(
            schedule = RecurringSchedule(
                RecurringFrequency.WEEKLY,
                interval = 2,
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            ),
            // Week of 5 January is the base week, so the aligned weeks are every other one from it.
            startDate = LocalDate(2026, 1, 5),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 27)
    }

    "a monthly rule rolls into next month once its day has passed" {
        val rule = ruleOf(
            schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(10)),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 5, 10)
    }

    "a monthly rule keeps a later day in the current month" {
        val rule = ruleOf(
            schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(10, 28)),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 28)
    }

    "a 31st rule lands on the last day of a short month" {
        val rule = ruleOf(
            schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(31)),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 4, 30)
    }

    "a 31st rule that skips short months waits for a long one" {
        val rule = ruleOf(
            schedule = RecurringSchedule(
                RecurringFrequency.MONTHLY,
                daysOfMonth = setOf(31),
                missingDayPolicy = MissingDayPolicy.SKIP,
            ),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2026, 5, 31)
    }

    "a yearly rule takes its own day next year once it has passed" {
        val rule = ruleOf(
            schedule = RecurringSchedule(RecurringFrequency.YEARLY),
            startDate = LocalDate(2026, 3, 4),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2027, 3, 4)
    }

    "a 29 February rule that skips short years waits for the leap one" {
        val rule = ruleOf(
            schedule = RecurringSchedule(
                RecurringFrequency.YEARLY,
                missingDayPolicy = MissingDayPolicy.SKIP,
            ),
            startDate = LocalDate(2024, 2, 29),
        )
        rule.nextOccurrenceOnOrAfter(today) shouldBe LocalDate(2028, 2, 29)
    }

    "an interval below one is read as every step rather than looping" {
        val rule = ruleOf(schedule = RecurringSchedule(RecurringFrequency.DAILY, interval = 0))
        rule.nextOccurrenceOnOrAfter(today) shouldBe today
    }

    "occurrences come back soonest first, capped at the limit" {
        val rules = listOf(
            ruleOf(id = "rent", schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(28))),
            ruleOf(id = "gym", schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(16))),
            ruleOf(id = "phone", schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(20))),
            ruleOf(id = "car", schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(30))),
        )
        rules.upcomingOccurrences(today = today, currency = USD, limit = 3)
            .map { it.title } shouldContainExactly listOf("gym", "phone", "rent")
    }

    "an inactive rule is left out" {
        val rules = listOf(
            ruleOf(schedule = RecurringSchedule(RecurringFrequency.DAILY), isActive = false),
        )
        rules.upcomingOccurrences(today = today, currency = USD, limit = 3).shouldBeEmpty()
    }

    "the day count is measured from the day the list was built" {
        val rules = listOf(
            ruleOf(id = "today", schedule = RecurringSchedule(RecurringFrequency.DAILY)),
            ruleOf(
                id = "later",
                schedule = RecurringSchedule(RecurringFrequency.MONTHLY, daysOfMonth = setOf(20)),
            ),
        )
        val upcoming = rules.upcomingOccurrences(today = today, currency = USD, limit = 3)

        upcoming.map { it.daysUntil } shouldContainExactly listOf(0, 5)
        // Today and tomorrow get the tint; five days out is still something to plan around.
        upcoming.map { it.isImminent } shouldContainExactly listOf(true, false)
    }

    "a rule the calendar cannot place is dropped rather than listed undated" {
        val rule = ruleOf(
            schedule = RecurringSchedule(
                RecurringFrequency.MONTHLY,
                daysOfMonth = setOf(0),
                missingDayPolicy = MissingDayPolicy.SKIP,
            ),
        )
        rule.nextOccurrenceOnOrAfter(today).shouldBeNull()
        listOf(rule).upcomingOccurrences(today = today, currency = USD, limit = 3).shouldBeEmpty()
    }
})
