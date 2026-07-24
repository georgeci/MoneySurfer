package com.georgeci.moneysurfer.data.db.entity

import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AccountExtraDetailsColumnTest : StringSpec({

    "details round-trip through the column, order included" {
        val details = listOf(
            AccountExtraDetail(key = "IBAN", value = "PL61 1090 1014 0000 0712 1981 2874"),
            AccountExtraDetail(key = "Broker code", value = "MS-4417"),
        )

        AccountExtraDetailsColumn.decode(AccountExtraDetailsColumn.encode(details)) shouldBe details
    }

    "a value containing quotes, commas and newlines survives the round trip" {
        val details = listOf(
            AccountExtraDetail(key = "DESCRIPTION", value = "Joint \"reserve\", 6 months\nof expenses"),
        )

        AccountExtraDetailsColumn.decode(AccountExtraDetailsColumn.encode(details)) shouldBe details
    }

    "encode normalizes, so blanks and duplicates never reach the column" {
        val encoded = AccountExtraDetailsColumn.encode(
            listOf(
                AccountExtraDetail(key = " IBAN ", value = "PL61"),
                AccountExtraDetail(key = "iban", value = "duplicate"),
                AccountExtraDetail(key = "BIC", value = "  "),
            ),
        )

        AccountExtraDetailsColumn.decode(encoded) shouldBe
            listOf(AccountExtraDetail(key = "IBAN", value = "PL61"))
    }

    "an empty list encodes to the column default" {
        AccountExtraDetailsColumn.encode(emptyList()) shouldBe AccountExtraDetailsColumn.EMPTY
    }

    "an empty, blank or unparseable column reads as no details rather than throwing" {
        AccountExtraDetailsColumn.decode(AccountExtraDetailsColumn.EMPTY) shouldBe emptyList()
        AccountExtraDetailsColumn.decode("") shouldBe emptyList()
        AccountExtraDetailsColumn.decode("not json") shouldBe emptyList()
        AccountExtraDetailsColumn.decode("""[{"key":"IBAN"}]""") shouldBe emptyList()
    }
})
