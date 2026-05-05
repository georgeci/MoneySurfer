package com.georgeci.moneysurfer.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class ResolveLegacyOperationDateTest : FunSpec({

    val formatter = TimeFormatter()

    test("valid stored ISO is preserved") {
        val operationAt = 0L // would resolve to 1970-01-01 in UTC if used
        resolveLegacyOperationDate(formatter, "2025-06-15", operationAt) shouldBe
            LocalDate(2025, 6, 15)
    }

    test("blank stored falls back to operationAt in UTC") {
        // 2025-01-01T23:30:00Z — in UTC the date is 2025-01-01,
        // but in any zone east of UTC (e.g. +03:00) it would already be 2025-01-02.
        val operationAt = 1_735_774_200_000L
        resolveLegacyOperationDate(formatter, "", operationAt) shouldBe
            LocalDate(2025, 1, 1)
    }

    test("malformed stored falls back to operationAt in UTC") {
        val operationAt = 1_735_774_200_000L
        resolveLegacyOperationDate(formatter, "not-a-date", operationAt) shouldBe
            LocalDate(2025, 1, 1)
    }

    test("epoch zero stored as blank resolves to 1970-01-01") {
        resolveLegacyOperationDate(formatter, "", 0L) shouldBe LocalDate(1970, 1, 1)
    }
})
