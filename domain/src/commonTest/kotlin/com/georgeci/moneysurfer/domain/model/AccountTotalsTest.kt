package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccountTotalsTest : StringSpec({

    "no accounts means no totals" {
        emptyList<Account>().totalsByCurrency() shouldBe emptyList()
    }

    "same-currency accounts collapse into one total" {
        val accounts = listOf(
            anAccount(id = accountId("a-1"), currencyCode = EUR, balance = Money.fromMajor(100)),
            anAccount(id = accountId("a-2"), currencyCode = EUR, balance = Money.fromMajor(25)),
        )

        accounts.totalsByCurrency() shouldBe listOf(CurrencyTotal(EUR, Money.fromMajor(125)))
    }

    "each currency keeps its own total instead of being summed together" {
        val accounts = listOf(
            anAccount(id = accountId("a-1"), currencyCode = EUR, balance = Money.fromMajor(100)),
            anAccount(id = accountId("a-2"), currencyCode = USD, balance = Money.fromMajor(40)),
            anAccount(id = accountId("a-3"), currencyCode = EUR, balance = Money.fromMajor(10)),
        )

        accounts.totalsByCurrency() shouldBe listOf(
            CurrencyTotal(EUR, Money.fromMajor(110)),
            CurrencyTotal(USD, Money.fromMajor(40)),
        )
    }

    "the most-used currency leads regardless of account order" {
        val usdFirst = listOf(
            anAccount(id = accountId("a-1"), currencyCode = USD, balance = Money.fromMajor(1)),
            anAccount(id = accountId("a-2"), currencyCode = EUR, balance = Money.fromMajor(2)),
            anAccount(id = accountId("a-3"), currencyCode = EUR, balance = Money.fromMajor(3)),
        )

        usdFirst.totalsByCurrency().first() shouldBe CurrencyTotal(EUR, Money.fromMajor(5))
    }

    "a tie on account count is broken by currency code so the order is stable" {
        val accounts = listOf(
            anAccount(id = accountId("a-1"), currencyCode = USD, balance = Money.fromMajor(1)),
            anAccount(id = accountId("a-2"), currencyCode = RUB, balance = Money.fromMajor(2)),
            anAccount(id = accountId("a-3"), currencyCode = EUR, balance = Money.fromMajor(3)),
        )

        accounts.totalsByCurrency().map { it.currencyCode } shouldBe listOf(EUR, RUB, USD)
    }
})
