package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TransactionsListMappingTest : StringSpec({

    "single account and category selections resolve their chip names" {
        val selectedAccount = anAccount(id = accountId("cash"), name = "Cash")
        val selectedCategory = aCategory(id = categoryId("food"), name = "Food")
        val filters = TransactionFilters(
            accountIds = setOf(selectedAccount.id),
            categoryIds = setOf(selectedCategory.id),
        )

        val result = chips(filters, listOf(selectedAccount), listOf(selectedCategory))

        result.accountCount shouldBe 1
        result.accountName shouldBe "Cash"
        result.categoryCount shouldBe 1
        result.categoryName shouldBe "Food"
    }

    "multiple selections expose counts but no misleading single name" {
        val firstAccount = anAccount(id = accountId("cash"), name = "Cash")
        val secondAccount = anAccount(id = accountId("card"), name = "Card")
        val filters = TransactionFilters(accountIds = setOf(firstAccount.id, secondAccount.id))

        val result = chips(filters, listOf(firstAccount, secondAccount), emptyList())

        result.accountCount shouldBe 2
        result.accountName shouldBe null
        result.categoryCount shouldBe 0
        result.categoryName shouldBe null
    }

    "a stale selected id keeps the count but has no resolved name" {
        val filters = TransactionFilters(accountIds = setOf(accountId("deleted")))

        val result = chips(filters, emptyList(), emptyList())

        result.accountCount shouldBe 1
        result.accountName shouldBe null
    }

    "an account-scoped summary always uses the account currency" {
        val eur = CurrencyCode("EUR")
        val account = anAccount(currencyCode = eur)
        val totals = listOf(
            TransactionTotal(TransactionType.INCOME, USD, 1_000.dollars),
            TransactionTotal(TransactionType.INCOME, eur, 1.dollars),
        )

        summaryCurrency(account, totals) shouldBe eur
    }

    "an all-account summary chooses the currency with the largest magnitude" {
        val eur = CurrencyCode("EUR")
        val totals = listOf(
            TransactionTotal(TransactionType.INCOME, USD, 10.dollars),
            TransactionTotal(TransactionType.EXPENSE, eur, 25.dollars),
        )

        summaryCurrency(null, totals) shouldBe eur
    }

    "an empty all-account summary falls back to USD" {
        summaryCurrency(null, emptyList()) shouldBe USD
    }

    "summary separates income and expense and calculates a positive net" {
        val totals = listOf(
            TransactionTotal(TransactionType.INCOME, USD, 50.dollars),
            TransactionTotal(TransactionType.EXPENSE, USD, 20.dollars),
        )

        val result = buildSummary(totals, USD)

        result.incomeFormatted shouldBe "+$50.00"
        result.expenseFormatted shouldBe "−$20.00"
        result.netFormatted shouldBe "+$30.00"
        result.netPositive shouldBe true
    }

    "summary formats a negative net and ignores totals in another currency" {
        val eur = CurrencyCode("EUR")
        val totals = listOf(
            TransactionTotal(TransactionType.INCOME, USD, 10.dollars),
            TransactionTotal(TransactionType.EXPENSE, USD, 25.dollars),
            TransactionTotal(TransactionType.INCOME, eur, 1_000.dollars),
        )

        val result = buildSummary(totals, USD)

        result.incomeFormatted shouldBe "+$10.00"
        result.expenseFormatted shouldBe "−$25.00"
        result.netFormatted shouldBe "−$15.00"
        result.netPositive shouldBe false
    }
})
