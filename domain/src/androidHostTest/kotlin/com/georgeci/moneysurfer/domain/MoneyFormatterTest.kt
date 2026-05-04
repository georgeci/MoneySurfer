package com.georgeci.moneysurfer.domain

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

class MoneyFormatterTest : StringSpec({

    lateinit var originalLocale: Locale

    beforeEach {
        originalLocale = Locale.getDefault()
    }

    afterEach {
        Locale.setDefault(originalLocale)
    }

    "format positive amount in USD with US locale" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(Money.fromMajor(42, 50), CurrencyCode("USD")) shouldBe "$42.50"
    }

    "format zero amount" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(Money.zero(), CurrencyCode("USD")) shouldBe "$0.00"
    }

    "format negative amount" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(-Money.fromMajor(10, 99), CurrencyCode("USD")) shouldContain "10.99"
    }

    "format large amount with grouping" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(Money.fromMajor(1_000_000), CurrencyCode("USD")) shouldBe "$1,000,000.00"
    }

    "format EUR with German locale" {
        Locale.setDefault(Locale.GERMANY)
        val result = MoneyFormatter.format(Money.fromMajor(1234, 56), CurrencyCode("EUR"))
        result shouldContain "1.234,56"
        result shouldContain "€"
    }

    "format GBP with UK locale" {
        Locale.setDefault(Locale.UK)
        MoneyFormatter.format(Money.fromMajor(99, 99), CurrencyCode("GBP")) shouldBe "£99.99"
    }

    "format single cent" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(Money.fromMinor(1), CurrencyCode("USD")) shouldBe "$0.01"
    }

    "format sub-dollar amount" {
        Locale.setDefault(Locale.US)
        MoneyFormatter.format(Money.fromMinor(99), CurrencyCode("USD")) shouldBe "$0.99"
    }
})
