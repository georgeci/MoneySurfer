package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

private val USD = CurrencyCode("USD")

private fun payload(
    result: String = "success",
    baseCode: String = "USD",
    timeLastUpdateUnix: Long = 1_735_689_600L,
    rates: Map<String, Double> = mapOf("EUR" to 0.95, "GBP" to 0.79),
) = ExchangeRateLatestDto(
    result = result,
    baseCode = baseCode,
    timeLastUpdateUnix = timeLastUpdateUnix,
    rates = rates,
)

/**
 * The provider is an unauthenticated third party the app cannot fix, so everything here is about
 * refusing a payload rather than trusting it: a table the UI would mislabel is worse than no table
 * at all, because the cached one it replaces was correct.
 */
class ExchangeRateApiTest : StringSpec({

    "the base currency is a path segment, so nothing needs encoding" {
        ExchangeRateApiConfig().latestUrl(USD) shouldBe "https://open.er-api.com/v6/latest/USD"
    }

    "a custom base url is honoured, which is what makes the provider swappable" {
        ExchangeRateApiConfig(baseUrl = "https://fx.test/v1").latestUrl(USD) shouldBe
            "https://fx.test/v1/USD"
    }

    "a successful payload becomes the domain table" {
        val table = payload().toDomain(USD)!!

        table.baseCurrency shouldBe USD
        table.rates shouldBe mapOf(CurrencyCode("EUR") to 0.95, CurrencyCode("GBP") to 0.79)
        table.asOf shouldBe Instant.fromEpochSeconds(1_735_689_600L)
    }

    "lower-case currency codes from the provider are normalized" {
        payload(rates = mapOf("eur" to 0.95)).toDomain(USD)!!.rates shouldBe
            mapOf(CurrencyCode("EUR") to 0.95)
    }

    "a base that only differs in case is still the one we asked for" {
        payload(baseCode = "usd").toDomain(USD)!!.baseCurrency shouldBe USD
    }

    "a non-success result is refused" {
        payload(result = "error").toDomain(USD).shouldBeNull()
    }

    "an empty quote set is refused" {
        payload(rates = emptyMap()).toDomain(USD).shouldBeNull()
    }

    // No publication moment means the "as of" footnote would have to invent one.
    "a payload with no publication timestamp is refused" {
        payload(timeLastUpdateUnix = 0L).toDomain(USD).shouldBeNull()
    }

    // A redirect or a provider fallback: quotes against a base nobody asked for would be applied
    // to the workspace's currency and silently misprice every balance.
    "a payload quoted against a different base is refused" {
        payload(baseCode = "EUR").toDomain(USD).shouldBeNull()
    }

    // A zero or negative rate reaches a division; an infinite balance is what comes back out.
    "unusable individual quotes are dropped, and the rest of the table stands" {
        val table = payload(
            rates = mapOf(
                "EUR" to 0.95,
                "ZERO" to 0.0,
                "NEG" to -1.0,
                "NAN" to Double.NaN,
                "INF" to Double.POSITIVE_INFINITY,
            ),
        ).toDomain(USD)!!

        table.rates shouldBe mapOf(CurrencyCode("EUR") to 0.95)
    }

    "a payload whose every quote is unusable maps to an empty table rather than a wrong one" {
        payload(rates = mapOf("ZERO" to 0.0)).toDomain(USD)!!.rates shouldBe emptyMap()
    }
})
