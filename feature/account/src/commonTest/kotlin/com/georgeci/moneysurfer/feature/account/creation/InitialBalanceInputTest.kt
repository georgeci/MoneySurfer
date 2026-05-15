package com.georgeci.moneysurfer.feature.account.creation

import com.georgeci.moneysurfer.domain.primitives.AccountType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InitialBalanceInputTest : StringSpec({

    "sanitize drops letters and other stray characters" {
        InitialBalanceInput.sanitize("1a2b3c") shouldBe "123"
        InitialBalanceInput.sanitize("12 EUR") shouldBe "12"
    }

    "sanitize keeps only the first decimal separator and normalises commas" {
        InitialBalanceInput.sanitize("12,34") shouldBe "12.34"
        InitialBalanceInput.sanitize("1.2.3.4") shouldBe "1.234"
        InitialBalanceInput.sanitize("1,2.3") shouldBe "1.23"
    }

    "sanitize keeps a single leading minus and ignores later ones" {
        InitialBalanceInput.sanitize("-50") shouldBe "-50"
        InitialBalanceInput.sanitize("5-0") shouldBe "50"
        InitialBalanceInput.sanitize("-1-2") shouldBe "-12"
    }

    "sanitize preserves an empty field" {
        InitialBalanceInput.sanitize("") shouldBe ""
        InitialBalanceInput.sanitize("abc") shouldBe ""
    }

    "parse returns null for incomplete input and a number otherwise" {
        InitialBalanceInput.parse("") shouldBe null
        InitialBalanceInput.parse("-") shouldBe null
        InitialBalanceInput.parse("12.34") shouldBe 12.34
        InitialBalanceInput.parse("0") shouldBe 0.0
    }

    "a zero or positive balance is always accepted" {
        InitialBalanceInput.errorFor("0", AccountType.CASH) shouldBe null
        InitialBalanceInput.errorFor("100", AccountType.SAVINGS) shouldBe null
    }

    "a negative balance is blocked for non-credit account types" {
        InitialBalanceInput.errorFor("-50", AccountType.CASH) shouldBe InitialBalanceError.NEGATIVE_NOT_ALLOWED
        InitialBalanceInput.errorFor("-50", AccountType.BANK) shouldBe InitialBalanceError.NEGATIVE_NOT_ALLOWED
        InitialBalanceInput.errorFor("-50", AccountType.SAVINGS) shouldBe InitialBalanceError.NEGATIVE_NOT_ALLOWED
    }

    "a negative balance is allowed for a credit (card) account" {
        InitialBalanceInput.errorFor("-50", AccountType.CARD) shouldBe null
    }
})
