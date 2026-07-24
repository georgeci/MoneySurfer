package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class AccountBalanceSeriesTest : StringSpec({

    val account = accountId()
    val other = accountId("a-2")
    val today = LocalDate(2024, 3, 31)

    fun daysAgo(count: Int): LocalDate = today.minus(count, DateTimeUnit.DAY)

    fun expense(id: String, amount: Int, date: LocalDate) = aTransaction(
        id = transactionId(id),
        accountId = account,
        money = amount.dollars,
        type = TransactionType.EXPENSE,
        operationDate = date,
    )

    fun income(id: String, amount: Int, date: LocalDate) = aTransaction(
        id = transactionId(id),
        accountId = account,
        money = amount.dollars,
        type = TransactionType.INCOME,
        operationDate = date,
    )

    "the last point is the current balance and the window has one point per day" {
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(expense("t-1", 40, daysAgo(3))),
            today = today,
            days = 7,
        )

        series.points shouldHaveSize 7
        series.points.first().date shouldBe daysAgo(6)
        series.points.last().date shouldBe today
        series.points.last().balance shouldBe 500.dollars
    }

    "walking back re-adds an expense before the day it was booked on" {
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(expense("t-1", 40, daysAgo(2))),
            today = today,
            days = 5,
        )

        val balances = series.points.map { it.balance }
        balances shouldBe listOf(540.dollars, 540.dollars, 500.dollars, 500.dollars, 500.dollars)
    }

    "the delta counts the oldest day's own transactions" {
        val oldest = daysAgo(4)
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(income("t-1", 100, oldest), expense("t-2", 30, daysAgo(1))),
            today = today,
            days = 5,
        )

        // Between the ends the curve only moves by the later expense; the delta also has to
        // carry the income booked on the first day of the window.
        series.points.last().balance - series.points.first().balance shouldBe (-30).dollars
        series.delta shouldBe 70.dollars
    }

    "transactions dated after today are backed out of the anchor" {
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(income("t-1", 200, today.plus(1, DateTimeUnit.DAY))),
            today = today,
            days = 3,
        )

        series.points.last().balance shouldBe 300.dollars
        series.delta shouldBe Money.zero()
    }

    "rows for other accounts and non-actual rows leave the curve flat" {
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(
                aTransaction(
                    id = transactionId("t-1"),
                    accountId = other,
                    money = 90.dollars,
                    type = TransactionType.EXPENSE,
                    operationDate = daysAgo(1),
                ),
                aTransaction(
                    id = transactionId("t-2"),
                    accountId = account,
                    money = 90.dollars,
                    type = TransactionType.EXPENSE,
                    status = TransactionStatus.PLANNED,
                    operationDate = daysAgo(1),
                ),
            ),
            today = today,
            days = 3,
        )

        series.points.map { it.balance } shouldBe List(3) { 500.dollars }
        series.delta shouldBe Money.zero()
    }

    "the opening balance is what the curve rises from" {
        val series = buildAccountBalanceSeries(
            accountId = account,
            currentBalance = 250.dollars,
            transactions = listOf(
                aTransaction(
                    id = transactionId("t-1"),
                    accountId = account,
                    money = 250.dollars,
                    type = TransactionType.OPENING_BALANCE,
                    operationDate = daysAgo(1),
                ),
            ),
            today = today,
            days = 3,
        )

        series.points.map { it.balance } shouldBe
            listOf(Money.zero(), 250.dollars, 250.dollars)
        series.delta shouldBe 250.dollars
    }

    "a non-positive window is a programming error" {
        shouldThrow<IllegalArgumentException> {
            buildAccountBalanceSeries(
                accountId = account,
                currentBalance = Money.zero(),
                transactions = emptyList(),
                today = today,
                days = 0,
            )
        }
    }
})
