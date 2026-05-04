package com.georgeci.moneysurfer.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AmountInputTransformationTest : FunSpec({

    context("accepts valid input") {
        withData(
            nameFn = { (input, expected) -> "'$input' -> '$expected'" },
            "5" to "5",
            "123" to "123",
            "0" to "0",
            "0." to "0.",
            "0.99" to "0.99",
            "42.5" to "42.5",
            "42.50" to "42.50",
            "42." to "42.",
            "." to ".",
            ".5" to ".5",
            ".99" to ".99",
            "999999" to "999999",
            "999999.99" to "999999.99",
        ) { (input, expected) ->
            AmountInputTransformation.validateAndNormalize(input) shouldBe expected
        }
    }

    context("normalizes comma to dot") {
        withData(
            nameFn = { (input, expected) -> "'$input' -> '$expected'" },
            "42,50" to "42.50",
            ",5" to ".5",
            "0,99" to "0.99",
        ) { (input, expected) ->
            AmountInputTransformation.validateAndNormalize(input) shouldBe expected
        }
    }

    context("rejects invalid input") {
        withData(
            nameFn = { (input, reason) -> "'$input' ($reason)" },
            "42.123" to "more than 2 cents",
            "1.999" to "three decimal digits",
            "007" to "leading zeros",
            "00" to "double leading zero",
            "00.5" to "leading zeros with decimal",
            "abc" to "letters",
            "12a" to "mixed letters and digits",
            "-5" to "negative sign",
            "+5" to "plus sign",
            "1.2.3" to "multiple dots",
            "1 2" to "spaces",
            "42,123" to "comma with three cents",
        ) { (input, _) ->
            AmountInputTransformation.validateAndNormalize(input).shouldBeNull()
        }
    }
})
