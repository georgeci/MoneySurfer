package com.georgeci.moneysurfer.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class TimeFormatterTest : FunSpec({

    val formatter = TimeFormatter()

    context("parseInstant / formatInstant round-trip") {
        withData(
            nameFn = { "epochMillis=$it" },
            0L,
            1_700_000_000_000L,
            -1_000L,
            Long.MAX_VALUE / 2,
        ) { millis ->
            val instant = formatter.parseInstant(millis)
            instant shouldBe Instant.fromEpochMilliseconds(millis)
            formatter.formatInstant(instant) shouldBe millis
        }
    }

    context("parseInstantOrNull / formatInstantOrNull") {
        test("null in -> null out") {
            formatter.parseInstantOrNull(null).shouldBeNull()
            formatter.formatInstantOrNull(null).shouldBeNull()
        }
        test("non-null pass-through") {
            val millis = 1_500_000_000_000L
            val instant = formatter.parseInstantOrNull(millis)
            instant shouldBe Instant.fromEpochMilliseconds(millis)
            formatter.formatInstantOrNull(instant) shouldBe millis
        }
    }

    context("parseLocalDate / formatLocalDate") {
        withData(
            nameFn = { "iso=$it" },
            "2025-01-31",
            "2026-05-05",
            "1970-01-01",
        ) { iso ->
            val date = formatter.parseLocalDate(iso)
            date shouldBe LocalDate.parse(iso)
            formatter.formatLocalDate(date) shouldBe iso
        }
    }

    context("parseLocalDateOrNull") {
        test("blank -> null") {
            formatter.parseLocalDateOrNull("").shouldBeNull()
            formatter.parseLocalDateOrNull("   ").shouldBeNull()
        }
        test("malformed -> null") {
            formatter.parseLocalDateOrNull("not-a-date").shouldBeNull()
            formatter.parseLocalDateOrNull("2025-13-40").shouldBeNull()
        }
        test("valid ISO -> LocalDate") {
            formatter.parseLocalDateOrNull("2025-06-15") shouldBe LocalDate(2025, 6, 15)
        }
    }
})
