package com.georgeci.moneysurfer.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccountExtraDetailsTest : StringSpec({

    "normalize trims both halves and collapses whitespace runs in the key" {
        AccountExtraDetails.normalize(
            listOf(AccountExtraDetail(key = "  Broker   code ", value = "  MS-4417 ")),
        ) shouldBe listOf(AccountExtraDetail(key = "Broker code", value = "MS-4417"))
    }

    "normalize drops entries with a blank key or a blank value" {
        AccountExtraDetails.normalize(
            listOf(
                AccountExtraDetail(key = "IBAN", value = "   "),
                AccountExtraDetail(key = "   ", value = "orphan"),
                AccountExtraDetail(key = "BIC", value = "INGBPLPW"),
            ),
        ) shouldBe listOf(AccountExtraDetail(key = "BIC", value = "INGBPLPW"))
    }

    "normalize keeps the first spelling of a case-insensitively duplicate key" {
        AccountExtraDetails.normalize(
            listOf(
                AccountExtraDetail(key = "IBAN", value = "first"),
                AccountExtraDetail(key = "iban", value = "second"),
            ),
        ) shouldBe listOf(AccountExtraDetail(key = "IBAN", value = "first"))
    }

    "normalize truncates over-long keys and values" {
        val normalized = AccountExtraDetails.normalize(
            listOf(AccountExtraDetail(key = "k".repeat(100), value = "v".repeat(1000))),
        ).single()

        normalized.key.length shouldBe AccountExtraDetails.MAX_KEY_LENGTH
        normalized.value.length shouldBe AccountExtraDetails.MAX_VALUE_LENGTH
    }

    "normalize caps the list at MAX_DETAILS" {
        val raw = (1..AccountExtraDetails.MAX_DETAILS + 5).map {
            AccountExtraDetail(key = "key-$it", value = "value-$it")
        }

        AccountExtraDetails.normalize(raw).size shouldBe AccountExtraDetails.MAX_DETAILS
    }

    "normalize preserves the order the user added the entries in" {
        val raw = listOf(
            AccountExtraDetail(key = "DESCRIPTION", value = "Joint reserve"),
            AccountExtraDetail(key = "IBAN", value = "PL61"),
        )

        AccountExtraDetails.normalize(raw) shouldBe raw
    }

    "a well-known key resolves to its enum entry and a custom label does not" {
        AccountExtraDetail(key = "CARD_LAST4", value = "4021").wellKnownKey shouldBe
            AccountExtraDetailKey.CARD_LAST4
        AccountExtraDetail(key = "Broker code", value = "MS-4417").wellKnownKey shouldBe null
        // Case matters: only the exact enum spelling is a well-known key, which is what keeps
        // a custom field named "iban" a custom field.
        AccountExtraDetail(key = "iban", value = "PL61").wellKnownKey shouldBe null
    }
})
