package com.georgeci.moneysurfer.feature.transaction.creation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TransactionAmountInputTest : StringSpec({

    "a plain positive amount is valid with no error" {
        TransactionAmountInput.parse("48.20") shouldBe 48.20
        TransactionAmountInput.isValid("48.20") shouldBe true
        TransactionAmountInput.errorFor("48.20") shouldBe null
    }

    "a comma is accepted as the decimal separator" {
        TransactionAmountInput.parse("12,50") shouldBe 12.50
        TransactionAmountInput.isValid("12,50") shouldBe true
        TransactionAmountInput.errorFor("12,50") shouldBe null
    }

    "multiple decimal separators are an invalid format" {
        TransactionAmountInput.parse("1.2.3") shouldBe null
        TransactionAmountInput.isValid("1.2.3") shouldBe false
        TransactionAmountInput.errorFor("1.2.3") shouldBe TransactionAmountError.INVALID_FORMAT
    }

    "mixed comma and dot separators are an invalid format" {
        TransactionAmountInput.errorFor("1,2.3") shouldBe TransactionAmountError.INVALID_FORMAT
    }

    "zero alone is not a valid transaction amount" {
        TransactionAmountInput.isValid("0") shouldBe false
        TransactionAmountInput.errorFor("0") shouldBe TransactionAmountError.NOT_POSITIVE
    }

    "zero with decimals like 000.00 is not a valid transaction amount" {
        TransactionAmountInput.isValid("000.00") shouldBe false
        TransactionAmountInput.errorFor("000.00") shouldBe TransactionAmountError.NOT_POSITIVE
    }

    "a blank field shows no error but does not validate" {
        TransactionAmountInput.errorFor("") shouldBe null
        TransactionAmountInput.errorFor("   ") shouldBe null
        TransactionAmountInput.isValid("") shouldBe false
    }

    "letters are an invalid format" {
        TransactionAmountInput.errorFor("12a") shouldBe TransactionAmountError.INVALID_FORMAT
        TransactionAmountInput.isValid("12a") shouldBe false
    }

    "negative amounts are an invalid format — the type toggle carries the sign" {
        TransactionAmountInput.errorFor("-5") shouldBe TransactionAmountError.INVALID_FORMAT
    }

    "exponent notation is an invalid format" {
        TransactionAmountInput.errorFor("1e5") shouldBe TransactionAmountError.INVALID_FORMAT
    }

    "a lone separator is an invalid format" {
        TransactionAmountInput.errorFor(".") shouldBe TransactionAmountError.INVALID_FORMAT
    }
})
