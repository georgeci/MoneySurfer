package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * One scheduled payment the dashboard is about to be hit by: which rule, what it costs, and the
 * day it next falls due.
 *
 * [daysUntil] travels beside [dueDate] rather than being re-derived on the screen — "today" is a
 * time zone away from the UI layer, and a card that computed it itself would drift from the list
 * it was sorted into the moment the two disagreed about which day it is.
 */
data class UpcomingRecurring(
    val ruleId: RecurringRuleId,
    val title: String,
    val amount: Money,
    val currency: CurrencyCode,
    val dueDate: LocalDate,
    /** Whole days from the day the list was built. `0` is today, never negative. */
    val daysUntil: Int,
) {

    /**
     * Whether this one is close enough to be worth the tint. Today and tomorrow only: the tint is
     * "you cannot plan around this any more", and everything further out is exactly what the rest
     * of the list is for.
     */
    val isImminent: Boolean get() = daysUntil <= IMMINENT_DAYS
}

/**
 * The next occurrence of each active rule, soonest first, capped at [limit].
 *
 * Rules the calendar can no longer place — see [nextOccurrenceOnOrAfter] — drop out rather than
 * being listed without a date. Ties break on the rule id so two payments due the same day keep a
 * stable order across emissions instead of swapping rows whenever the query re-runs.
 */
fun List<RecurringRule>.upcomingOccurrences(
    today: LocalDate,
    currency: CurrencyCode,
    limit: Int,
): List<UpcomingRecurring> = asSequence()
    .filter { it.isActive }
    .mapNotNull { rule ->
        rule.nextOccurrenceOnOrAfter(today)?.let { due ->
            UpcomingRecurring(
                ruleId = rule.id,
                title = rule.title,
                amount = rule.amount,
                currency = currency,
                dueDate = due,
                daysUntil = (due.toEpochDays() - today.toEpochDays()).toInt(),
            )
        }
    }
    .sortedWith(compareBy({ it.dueDate }, { it.ruleId.value }))
    .take(limit)
    .toList()

/**
 * The first day on or after [date] that this rule fires, or null when there is none within the
 * probing horizon below.
 *
 * Derived from the schedule rather than read from [RecurringRule.nextRunAt]: that field is the
 * generator's own bookkeeping — null on every rule that has not run yet, and stale on any device
 * that pulled the rule before the run happened — so a widget reading it would show a date the
 * calendar disagrees with. The schedule is the thing the user actually configured.
 *
 * A rule due *today* counts as due today even if it has already been generated. Whether a given
 * occurrence has been booked is the generator's question, and this file has no transactions to ask
 * it with; showing today's rent one hour after it posted is a far smaller error than hiding
 * tomorrow's.
 */
fun RecurringRule.nextOccurrenceOnOrAfter(date: LocalDate): LocalDate? {
    // Never before the rule itself begins, and never below a one-step interval: a zero or negative
    // interval reaches the domain from remote sync, and stepping by it would not terminate.
    val from = maxOf(date, startDate)
    val step = schedule.interval.coerceAtLeast(1)
    return when (schedule.frequency) {
        RecurringFrequency.DAILY -> nextDaily(from, step)
        RecurringFrequency.WEEKLY -> nextWeekly(from, step)
        RecurringFrequency.MONTHLY -> nextMonthly(from, step)
        RecurringFrequency.YEARLY -> nextYearly(from, step)
    }
}

/** Every [step] days from the start date — the one frequency with nothing to clamp. */
private fun RecurringRule.nextDaily(from: LocalDate, step: Int): LocalDate {
    val elapsed = from.toEpochDays() - startDate.toEpochDays()
    val stepsTaken = ceilDiv(elapsed, step.toLong())
    return startDate.plus(stepsTaken * step, DateTimeUnit.DAY)
}

/**
 * The selected weekdays of every [step]-th week, counted from the week the rule starts in. A rule
 * that names no weekday keeps the one its start date falls on.
 *
 * Two weeks are probed, not one: the first aligned week can be the week [from] sits in, whose
 * matching days may all be behind it already.
 */
private fun RecurringRule.nextWeekly(from: LocalDate, step: Int): LocalDate? {
    val weekdays = schedule.daysOfWeek.ifEmpty { setOf(startDate.dayOfWeek) }
    val base = startDate.startOfWeek()
    val elapsedWeeks = (from.startOfWeek().toEpochDays() - base.toEpochDays()) / DAYS_PER_WEEK
    val alignedWeeks = ceilDiv(elapsedWeeks, step.toLong()) * step
    for (probe in 0 until WEEK_PROBES) {
        val weekStart = base.plus((alignedWeeks + probe.toLong() * step) * DAYS_PER_WEEK, DateTimeUnit.DAY)
        weekdays
            .map { weekStart.plus((it.isoDayNumber - 1).toLong(), DateTimeUnit.DAY) }
            .filter { it >= from }
            .minOrNull()
            ?.let { return it }
    }
    return null
}

/**
 * The selected days of every [step]-th month, counted from the month the rule starts in. A rule
 * that names no day of the month keeps the one its start date falls on.
 *
 * Several months are probed because a month can legitimately yield nothing: under
 * [MissingDayPolicy.SKIP] a "the 31st" rule simply does not fire in February.
 */
private fun RecurringRule.nextMonthly(from: LocalDate, step: Int): LocalDate? {
    val days = schedule.daysOfMonth.ifEmpty { setOf(startDate.day) }
    val elapsedMonths = monthIndex(from) - monthIndex(startDate)
    val alignedMonths = ceilDiv(elapsedMonths, step.toLong()) * step
    val base = LocalDate(startDate.year, startDate.month, 1)
    for (probe in 0 until MONTH_PROBES) {
        val monthStart = base.plus(alignedMonths + probe.toLong() * step, DateTimeUnit.MONTH)
        days
            .mapNotNull { resolveDayOfMonth(monthStart, it) }
            .filter { it >= from }
            .minOrNull()
            ?.let { return it }
    }
    return null
}

/**
 * The start date's own day and month, every [step]-th year. Probed for the same reason the monthly
 * branch is: a February 29th rule under [MissingDayPolicy.SKIP] only fires in leap years.
 */
private fun RecurringRule.nextYearly(from: LocalDate, step: Int): LocalDate? {
    val elapsedYears = (from.year - startDate.year).toLong()
    val alignedYears = ceilDiv(elapsedYears, step.toLong()) * step
    for (probe in 0 until YEAR_PROBES) {
        val year = startDate.year + (alignedYears + probe.toLong() * step).toInt()
        val candidate = resolveDayOfMonth(LocalDate(year, startDate.month, 1), startDate.day)
        if (candidate != null && candidate >= from) return candidate
    }
    return null
}

/**
 * [day] placed inside the month [monthStart] opens, or null when that month is too short and the
 * rule's [MissingDayPolicy] says to skip it rather than pull the payment onto the last day.
 */
private fun RecurringRule.resolveDayOfMonth(monthStart: LocalDate, day: Int): LocalDate? {
    val lastDay = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return when {
        day < 1 -> null
        day <= lastDay.day -> LocalDate(monthStart.year, monthStart.month, day)
        schedule.missingDayPolicy == MissingDayPolicy.LAST_DAY_OF_MONTH -> lastDay
        else -> null
    }
}

/** Monday of the week [this] falls in — the anchor weekly alignment is counted from. */
private fun LocalDate.startOfWeek(): LocalDate =
    minus((dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY)

/** Months since year zero, so two dates can be subtracted into a plain month count. */
private fun monthIndex(date: LocalDate): Long = date.year.toLong() * MONTHS_PER_YEAR + date.month.number

/** Ceiling division for non-negative [value] — how many whole [step]s it takes to reach it. */
private fun ceilDiv(value: Long, step: Long): Long =
    if (value <= 0) 0 else (value + step - 1) / step

/** See [UpcomingRecurring.isImminent] — today and tomorrow. */
private const val IMMINENT_DAYS = 1

private const val DAYS_PER_WEEK = 7
private const val MONTHS_PER_YEAR = 12

/** The aligned week [nextWeekly] probes plus the one [from] may already have run past. */
private const val WEEK_PROBES = 2

/** A year of aligned months — long enough for any day-of-month a short month can skip. */
private const val MONTH_PROBES = 12

/** Two leap cycles of aligned years, which covers a February 29th rule under any interval up to 4. */
private const val YEAR_PROBES = 8
