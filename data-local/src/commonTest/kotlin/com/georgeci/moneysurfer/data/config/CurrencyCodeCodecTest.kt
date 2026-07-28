package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CurrencyCodeCodecTest : StringSpec({

    "a code round-trips unchanged" {
        val encoded = CurrencyCodeCodec.encode(CurrencyCode("PLN"))

        encoded shouldBe "PLN"
        CurrencyCodeCodec.decode(encoded) shouldBe CurrencyCode("PLN")
    }

    "a hand-typed code is trimmed and upcased" {
        CurrencyCodeCodec.decode("  eur ") shouldBe CurrencyCode("EUR")
    }

    "anything that is not three A-Z letters is undecodable, not a default" {
        // The debug panel writes this key as free text, so these are all reachable. Undecodable
        // is the answer that lets the layer below win; a substituted default would look deliberate.
        listOf(
            "",
            "EU",
            "EURO",
            "E1R",
            "€UR",
            // Unicode-wide letters: `Char.isLetter` would wave these through as a "currency code".
            "руб",
            "евр",
        ).forEach { raw -> CurrencyCodeCodec.decode(raw).shouldBeNull() }
    }

    "a code the codec cannot re-decode is never produced" {
        // "aßc" is three letters, but upcasing expands ß to SS. Checking the length first would
        // hand back CurrencyCode("ASSC"), which encodes to a string this same codec then rejects —
        // the stored setting would vanish on the next read.
        CurrencyCodeCodec.decode("aßc").shouldBeNull()
    }

    "every decoded code survives a re-encode" {
        listOf("usd", "GBP", " gel ", "kzt").forEach { raw ->
            val decoded = CurrencyCodeCodec.decode(raw)
            decoded shouldBe CurrencyCodeCodec.decode(CurrencyCodeCodec.encode(decoded!!))
        }
    }
})
