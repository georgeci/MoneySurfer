package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val GBP = CurrencyCode("GBP")

class ConvertAccountsTotalUseCaseTest : StringSpec({

    val convert = ConvertAccountsTotalUseCase()

    "sums every currency into the base once rates cover them" {
        // 100 USD + 50 EUR (at 0.5 EUR per USD → 100 USD) + 1000 RUB (at 100 per USD → 10 USD)
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = EUR, balance = 50.dollars),
            anAccount(currencyCode = RUB, balance = 1000.dollars),
        )

        val result = convert(accounts, USD, anExchangeRateTable())

        result.total shouldBe 210.dollars
        result.unconverted.shouldBeEmpty()
        result.isComplete shouldBe true
        result.asOf shouldBe testInstant
    }

    "an account whose currency has no rate is listed, never dropped" {
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = GBP, balance = 40.dollars),
        )

        val result = convert(accounts, USD, anExchangeRateTable())

        result.total shouldBe 100.dollars
        result.unconverted.map { it.currencyCode } shouldBe listOf(GBP)
        result.unconverted.map { it.amount } shouldBe listOf(40.dollars)
        result.isComplete shouldBe false
    }

    "with no rates cached only the base-currency accounts fold in" {
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = EUR, balance = 50.dollars),
        )

        val result = convert(accounts, USD, rates = null)

        result.total shouldBe 100.dollars
        result.unconverted.map { it.currencyCode } shouldBe listOf(EUR)
        result.asOf.shouldBeNull()
    }

    "no account priceable in the base leaves no headline at all" {
        val result = convert(listOf(anAccount(currencyCode = GBP, balance = 40.dollars)), USD, null)

        result.total.shouldBeNull()
        result.unconverted.map { it.currencyCode } shouldBe listOf(GBP)
    }

    "an empty account list has no total and nothing left over" {
        val result = convert(emptyList(), USD, anExchangeRateTable())

        result.total.shouldBeNull()
        result.unconverted.shouldBeEmpty()
        result.baseCurrency shouldBe USD
        result.isComplete shouldBe true
    }

    "a single-currency workspace needs no rates and claims no as-of date" {
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = USD, balance = 25.dollars),
        )

        val result = convert(accounts, USD, anExchangeRateTable())

        result.total shouldBe 125.dollars
        result.asOf.shouldBeNull()
    }

    "negative balances convert too — an overdraft is still part of the total" {
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = EUR, balance = -(20.dollars)),
        )

        val result = convert(accounts, USD, anExchangeRateTable())

        result.total shouldBe 60.dollars
    }

    "a table quoted against another base is not trusted to price this workspace" {
        val accounts = listOf(
            anAccount(currencyCode = USD, balance = 100.dollars),
            anAccount(currencyCode = RUB, balance = 1000.dollars),
        )

        val result = convert(accounts, USD, anExchangeRateTable(base = EUR, rates = mapOf(RUB to 100.0)))

        result.total shouldBe 100.dollars
        result.unconverted.map { it.currencyCode } shouldBe listOf(RUB)
    }

    "a zero or non-finite quote counts as missing rather than as an infinite balance" {
        val accounts = listOf(anAccount(currencyCode = EUR, balance = 50.dollars))

        val result = convert(accounts, USD, anExchangeRateTable(rates = mapOf(EUR to 0.0)))

        result.total.shouldBeNull()
        result.unconverted.map { it.currencyCode } shouldBe listOf(EUR)
    }

    "a quote small enough to overflow the total counts as missing, not as a clamped balance" {
        val accounts = listOf(anAccount(currencyCode = EUR, balance = 50.dollars))

        val result = convert(accounts, USD, anExchangeRateTable(rates = mapOf(EUR to 1e-12)))

        result.total.shouldBeNull()
        result.unconverted.map { it.currencyCode } shouldBe listOf(EUR)
    }
})
