package com.georgeci.moneysurfer.feature.transaction.filter

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/** A Thursday, mid-month, mid-week — the same fixed "today" the list tests use. */
private val TODAY = LocalDate(2025, 3, 27)

/**
 * The pure half of filtering: how a typed amount is read, and which window a date range resolves
 * to. Both are the places where a quiet mistake would show up as "the filter does nothing".
 */
class TransactionFilterMatchingTest : StringSpec({

    "typed precision is the tolerance" {
        parseAmountQuery("12") shouldBe AmountRange(1200, 1299)
        parseAmountQuery("12.5") shouldBe AmountRange(1250, 1259)
        parseAmountQuery("12.50") shouldBe AmountRange(1250, 1250)
    }

    "amounts are read however they are written" {
        parseAmountQuery(" 12,50 ") shouldBe AmountRange(1250, 1250)
        parseAmountQuery("+12.50") shouldBe AmountRange(1250, 1250)
        parseAmountQuery("1_000") shouldBe AmountRange(100_000, 100_099)
    }

    "what is not an amount stays not an amount" {
        parseAmountQuery("").shouldBeNull()
        parseAmountQuery("coffee").shouldBeNull()
        parseAmountQuery(".").shouldBeNull()
        parseAmountQuery(".5").shouldBeNull()
        parseAmountQuery("12.345").shouldBeNull()
        parseAmountQuery("1.2.3").shouldBeNull()
        parseAmountQuery("99999999999999").shouldBeNull()
    }

    "searching an amount finds the cents the user did not type" {
        val row = row(minor = 1240)

        TransactionFilters(query = "12").matches(row) shouldBe true
        TransactionFilters(query = "13").matches(row) shouldBe false
    }

    "a preset owns the window, and only while it is set" {
        resolveWindow(
            TransactionDateRange.Preset(TransactionDatePreset.Today),
            TransactionPeriodMode.Month,
            anchor = LocalDate(2024, 1, 1),
            today = TODAY,
        ) shouldBe TransactionPeriodWindow(TODAY, TODAY)

        // Follow-period defers to the pager's anchor, not to today.
        resolveWindow(
            TransactionDateRange.FollowPeriod,
            TransactionPeriodMode.Month,
            anchor = LocalDate(2024, 1, 15),
            today = TODAY,
        ) shouldBe TransactionPeriodWindow(LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))
    }

    "last month is the whole previous month, not thirty days back" {
        resolveWindow(
            TransactionDateRange.Preset(TransactionDatePreset.LastMonth),
            TransactionPeriodMode.Month,
            anchor = TODAY,
            today = TODAY,
        ) shouldBe TransactionPeriodWindow(LocalDate(2025, 2, 1), LocalDate(2025, 2, 28))
    }

    "a custom range may be open at either end" {
        resolveWindow(
            TransactionDateRange.Custom(from = LocalDate(2025, 1, 1), to = null),
            TransactionPeriodMode.Month,
            anchor = TODAY,
            today = TODAY,
        ) shouldBe TransactionPeriodWindow(from = LocalDate(2025, 1, 1), to = null)
    }

    "a half-typed bound reads as the digits so far, and nonsense is ignored" {
        val row = row(minor = 1240)

        // A trailing separator is still "twelve": the list must not empty out mid-keystroke.
        parseAmountQuery("12.") shouldBe AmountRange(1200, 1299)
        TransactionFilters(minAmount = "coffee").matches(row) shouldBe true
        TransactionFilters(minAmount = "20").matches(row) shouldBe false
    }

    "the badge counts filters, not the search text" {
        TransactionFilters(query = "coffee").activeCount shouldBe 0
        TransactionFilters(minAmount = "5", maxAmount = "50").activeCount shouldBe 1
        TransactionFilters(recurringOnly = true, plannedOnly = true).activeCount shouldBe 2
    }

    "the type segments partition the list, transfer legs included" {
        val expense = row(minor = 1000)
        val income = CategorizedTransaction(
            transaction = expense.transaction.copy(type = TransactionType.INCOME),
            categoryName = "Salary",
        )
        // Both legs of a transfer are stored as an ordinary EXPENSE/INCOME pair — only the
        // transferId tells them apart, which is why `Expenses` must not keep the outgoing one.
        val outgoingLeg = CategorizedTransaction(
            transaction = expense.transaction.copy(transferId = TransferId("tr-1")),
            categoryName = "Transfer",
        )
        val incomingLeg = CategorizedTransaction(
            transaction = income.transaction.copy(transferId = TransferId("tr-1")),
            categoryName = "Transfer",
        )

        val expenses = TransactionFilters(type = TransactionTypeFilter.Expenses)
        expenses.matches(expense) shouldBe true
        expenses.matches(outgoingLeg) shouldBe false

        val incomes = TransactionFilters(type = TransactionTypeFilter.Income)
        incomes.matches(income) shouldBe true
        incomes.matches(incomingLeg) shouldBe false

        val transfers = TransactionFilters(type = TransactionTypeFilter.Transfer)
        transfers.matches(outgoingLeg) shouldBe true
        transfers.matches(incomingLeg) shouldBe true
        transfers.matches(expense) shouldBe false
        transfers.matches(income) shouldBe false
    }

    "reset drops the filters and keeps the search text" {
        val cleared = TransactionFilters(query = "coffee", recurringOnly = true).cleared()

        cleared.query shouldBe "coffee"
        cleared.recurringOnly shouldBe false
    }
})

private fun row(minor: Long): CategorizedTransaction = CategorizedTransaction(
    transaction = aTransaction(money = Money.fromMinor(minor)),
    categoryName = "Groceries",
)
