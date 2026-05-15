package com.georgeci.moneysurfer.feature.workspace.firstrun

import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun currency(code: String, symbol: String = code, name: String = code): Currency =
    Currency(CurrencyCode(code), symbol = symbol, displayName = name)

class CurrencyPickerCatalogTest : StringSpec({

    "orderedForPicker puts preferred codes first in declared order" {
        val input = listOf(
            currency("PLN"),
            currency("UAH"),
            currency("USD"),
            currency("GEL"),
            currency("EUR"),
        )

        input.orderedForPicker().map { it.code.value } shouldBe
            listOf("USD", "EUR", "GEL", "UAH", "PLN")
    }

    "orderedForPicker sorts non-preferred codes alphabetically after preferred ones" {
        val input = listOf(
            currency("ZAR"),
            currency("AUD"),
            currency("RUB"),
            currency("CHF"),
        )

        input.orderedForPicker().map { it.code.value } shouldBe
            listOf("RUB", "AUD", "CHF", "ZAR")
    }

    "orderedForPicker keeps an empty list empty" {
        emptyList<Currency>().orderedForPicker() shouldBe emptyList()
    }

    "filteredBy with a blank query returns the list unchanged" {
        val input = listOf(currency("USD"), currency("EUR"))
        input.filteredBy("   ") shouldBe input
    }

    "filteredBy matches on currency code, case-insensitively" {
        val input = listOf(currency("USD"), currency("EUR"), currency("GEL"))
        input.filteredBy("eur").map { it.code.value } shouldBe listOf("EUR")
    }

    "filteredBy matches on display name" {
        val input = listOf(
            currency("USD", name = "US Dollar"),
            currency("GEL", name = "Georgian Lari"),
        )
        input.filteredBy("georgian").map { it.code.value } shouldBe listOf("GEL")
    }

    "filteredBy matches on symbol" {
        val input = listOf(
            currency("USD", symbol = "$"),
            currency("GEL", symbol = "₾"),
        )
        input.filteredBy("₾").map { it.code.value } shouldBe listOf("GEL")
    }

    "filteredBy returns no matches when nothing fits the query" {
        val input = listOf(currency("USD"), currency("EUR"))
        input.filteredBy("xyz") shouldBe emptyList()
    }
})
