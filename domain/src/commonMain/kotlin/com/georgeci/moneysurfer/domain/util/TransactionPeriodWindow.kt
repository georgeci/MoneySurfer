package com.georgeci.moneysurfer.domain.util

import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val DAYS_PER_WEEK = 7

/** Epoch day 0 (1970-01-01) was a Thursday, so Monday is three days later. */
private const val EPOCH_DAY_TO_MONDAY_OFFSET = 3

/** ISO-8601 pins week 1 as the week containing January 4th. */
private const val ISO_WEEK_ANCHOR_DAY = 4

/**
 * A closed date window `[from, to]` the transactions list is scoped to, or an unbounded one
 * (both ends `null`) for [TransactionPeriodMode.AllTime].
 *
 * Closed rather than half-open because the bounds go straight into a SQL
 * `operationDate BETWEEN` comparison against ISO date text — see `TransactionDao`.
 */
data class TransactionPeriodWindow(
    val from: LocalDate?,
    val to: LocalDate?,
) {
    val isUnbounded: Boolean get() = from == null && to == null

    operator fun contains(date: LocalDate): Boolean =
        (from == null || date >= from) && (to == null || date <= to)

    companion object {
        val Unbounded = TransactionPeriodWindow(from = null, to = null)
    }
}

/**
 * The window of [mode] containing [anchor]. Pure — the caller supplies the date, so there is no
 * `Clock` dependency and the maths stays trivially testable.
 */
fun periodWindow(mode: TransactionPeriodMode, anchor: LocalDate): TransactionPeriodWindow =
    when (mode) {
        TransactionPeriodMode.Month -> {
            val start = LocalDate(anchor.year, anchor.month, 1)
            TransactionPeriodWindow(start, start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
        }
        TransactionPeriodMode.Week -> {
            val start = anchor.startOfIsoWeek()
            TransactionPeriodWindow(start, start.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY))
        }
        TransactionPeriodMode.AllTime -> TransactionPeriodWindow.Unbounded
    }

/**
 * [anchor] shifted by [by] whole periods (negative goes back). A no-op for
 * [TransactionPeriodMode.AllTime], which has nothing to page through.
 *
 * Month steps are taken from the first of the month so a short-month clamp can never
 * accumulate: Jan 31 → Feb 28 → Mar 28 is avoided by anchoring on Jan 1 → Feb 1 → Mar 1.
 */
fun shiftPeriod(mode: TransactionPeriodMode, anchor: LocalDate, by: Int): LocalDate = when (mode) {
    TransactionPeriodMode.Month -> LocalDate(anchor.year, anchor.month, 1).plus(by, DateTimeUnit.MONTH)
    TransactionPeriodMode.Week -> anchor.startOfIsoWeek().plus(by * DAYS_PER_WEEK, DateTimeUnit.DAY)
    TransactionPeriodMode.AllTime -> anchor
}

/** The Monday of the ISO week containing this date. */
fun LocalDate.startOfIsoWeek(): LocalDate {
    val epochDay = toEpochDays()
    val daysSinceMonday = (epochDay + EPOCH_DAY_TO_MONDAY_OFFSET).mod(DAYS_PER_WEEK.toLong())
    return LocalDate.fromEpochDays(epochDay - daysSinceMonday)
}

/**
 * ISO-8601 week number (1..53) and the week-based year it belongs to. The two can disagree with
 * the calendar year around New Year: 2027-01-01 is week 53 of week-year 2026.
 */
data class IsoWeek(val weekNumber: Int, val weekYear: Int)

fun LocalDate.isoWeek(): IsoWeek {
    val weekStart = startOfIsoWeek()
    // The week-based year is the calendar year of the week's Thursday, by definition.
    val weekYear = weekStart.plus(EPOCH_DAY_TO_MONDAY_OFFSET, DateTimeUnit.DAY).year
    val firstWeekStart = LocalDate(weekYear, 1, ISO_WEEK_ANCHOR_DAY).startOfIsoWeek()
    val weeksBetween = (weekStart.toEpochDays() - firstWeekStart.toEpochDays()) / DAYS_PER_WEEK
    return IsoWeek(weekNumber = weeksBetween.toInt() + 1, weekYear = weekYear)
}
