package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Aggregate numbers for a date range. Pure domain return shape — never persisted, never
 * serialized, never pushed. byCategory / byAccount are intentionally omitted; those are
 * separate use cases (charts, accounts breakdown).
 */
data class PeriodTotals(
    val income: Money,
    val expense: Money,
    val net: Money,
    val plannedIncome: Money,
    val plannedExpense: Money,
) {
    companion object {
        val Empty = PeriodTotals(
            income = Money.zero(),
            expense = Money.zero(),
            net = Money.zero(),
            plannedIncome = Money.zero(),
            plannedExpense = Money.zero(),
        )
    }
}

/**
 * Pure aggregation over an in-memory list of transactions. Caller decides which list to
 * pass (typically `transactionRepository.getByWorkspaceId(...)`).
 *
 * Period is computed from `transaction.timestamp` interpreted in [timeZone]. Inclusive on
 * both ends. Deleted transactions are absent from the local table — no extra filtering.
 */
fun calculatePeriodTotalsFromList(
    transactions: List<Transaction>,
    fromDate: LocalDate,
    toDate: LocalDate,
    timeZone: TimeZone,
): PeriodTotals {
    var income = 0L
    var expense = 0L
    var plannedIncome = 0L
    var plannedExpense = 0L

    for (tx in transactions) {
        val date = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(timeZone).date
        if (date < fromDate || date > toDate) continue

        val magnitude = tx.money.abs().minor
        when (tx.status) {
            TransactionStatus.ACTUAL -> when (tx.type) {
                TransactionType.INCOME -> income += magnitude
                TransactionType.EXPENSE -> expense += magnitude
                TransactionType.OPENING_BALANCE -> Unit
            }
            TransactionStatus.PLANNED -> when (tx.type) {
                TransactionType.INCOME -> plannedIncome += magnitude
                TransactionType.EXPENSE -> plannedExpense += magnitude
                TransactionType.OPENING_BALANCE -> Unit
            }
        }
    }

    return PeriodTotals(
        income = Money.fromMinor(income),
        expense = Money.fromMinor(expense),
        net = Money.fromMinor(income - expense),
        plannedIncome = Money.fromMinor(plannedIncome),
        plannedExpense = Money.fromMinor(plannedExpense),
    )
}
