package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class PeriodTotalsTest : StringSpec({

    val tz = TimeZone.UTC
    val from = LocalDate(2026, 4, 1)
    val to = LocalDate(2026, 4, 30)

    fun atDate(year: Int, month: Int, day: Int): kotlin.time.Instant =
        LocalDateTime(year, month, day, 12, 0).toInstant(tz)

    val a = accountId()
    val ts = atDate(2026, 4, 15)

    "income and expense aggregate separately" {
        val list = listOf(
            aTransaction(accountId = a, money = Money.fromMinor(300), type = TransactionType.INCOME, operationAt = ts),
            aTransaction(accountId = a, money = Money.fromMinor(120), type = TransactionType.EXPENSE, operationAt = ts),
            aTransaction(accountId = a, money = Money.fromMinor(80), type = TransactionType.EXPENSE, operationAt = ts),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.income shouldBe Money.fromMinor(300)
        totals.expense shouldBe Money.fromMinor(200)
        totals.net shouldBe Money.fromMinor(100)
    }

    "opening_balance is excluded from income/expense" {
        val list = listOf(
            aTransaction(
                accountId = a,
                money = Money.fromMinor(1000),
                type = TransactionType.OPENING_BALANCE,
                operationAt = ts,
            ),
            aTransaction(accountId = a, money = Money.fromMinor(50), type = TransactionType.EXPENSE, operationAt = ts),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.income shouldBe Money.zero()
        totals.expense shouldBe Money.fromMinor(50)
        totals.net shouldBe Money.fromMinor(-50)
    }

    "planned rows feed plannedIncome/plannedExpense" {
        val list = listOf(
            aTransaction(
                accountId = a,
                money = Money.fromMinor(700),
                type = TransactionType.INCOME,
                status = TransactionStatus.PLANNED,
                operationAt = ts,
            ),
            aTransaction(
                accountId = a,
                money = Money.fromMinor(200),
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PLANNED,
                operationAt = ts,
            ),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.plannedIncome shouldBe Money.fromMinor(700)
        totals.plannedExpense shouldBe Money.fromMinor(200)
        totals.income shouldBe Money.zero()
        totals.expense shouldBe Money.zero()
    }

    "transactions outside the period are skipped" {
        val list = listOf(
            aTransaction(
                accountId = a,
                money = Money.fromMinor(500),
                type = TransactionType.INCOME,
                operationAt = atDate(2026, 3, 31),
            ),
            aTransaction(
                accountId = a,
                money = Money.fromMinor(400),
                type = TransactionType.INCOME,
                operationAt = atDate(2026, 5, 1),
            ),
            aTransaction(
                accountId = a,
                money = Money.fromMinor(100),
                type = TransactionType.INCOME,
                operationAt = atDate(2026, 4, 15),
            ),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.income shouldBe Money.fromMinor(100)
    }

    "boundary dates are inclusive" {
        val list = listOf(
            aTransaction(
                accountId = a,
                money = Money.fromMinor(10),
                type = TransactionType.INCOME,
                operationAt = atDate(2026, 4, 1),
            ),
            aTransaction(
                accountId = a,
                money = Money.fromMinor(20),
                type = TransactionType.INCOME,
                operationAt = atDate(2026, 4, 30),
            ),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.income shouldBe Money.fromMinor(30)
    }

    "empty list yields zero totals" {
        calculatePeriodTotalsFromList(emptyList(), from, to, tz) shouldBe PeriodTotals.Empty
    }

    "abs(money) handles legacy signed expense correctly" {
        val list = listOf(
            aTransaction(
                accountId = a,
                money = -Money.fromMinor(150),
                type = TransactionType.EXPENSE,
                operationAt = ts,
            ),
        )
        val totals = calculatePeriodTotalsFromList(list, from, to, tz)
        totals.expense shouldBe Money.fromMinor(150)
    }
})
