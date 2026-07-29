package com.georgeci.moneysurfer.utils

import androidx.compose.foundation.text.input.TextFieldState
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

    // The rules above are only half the contract: the field also has to *do* something with a
    // rejected edit. Driving the real InputTransformation over a buffer is what shows the
    // rejection puts the previous text back rather than leaving the bad keystroke in place.
    context("applies the rules to the edit buffer") {
        test("an accepted edit is left alone") {
            edited(previous = "4", typed = "42") shouldBe "42"
        }

        test("clearing the field is always allowed") {
            edited(previous = "42.50", typed = "") shouldBe ""
        }

        test("a comma is rewritten in place") {
            edited(previous = "42", typed = "42,") shouldBe "42."
        }

        test("a rejected edit puts the previous text back") {
            edited(previous = "42", typed = "42a") shouldBe "42"
        }

        test("a third decimal digit is rejected, keeping two") {
            edited(previous = "42.50", typed = "42.501") shouldBe "42.50"
        }
    }
})

/** Applies the transformation to [typed] the way a keystroke reaches it, and reports what stuck. */
private fun edited(previous: String, typed: String): String =
    TextFieldState(previous)
        .apply {
            edit {
                replace(0, length, typed)
                with(AmountInputTransformation) { transformInput() }
            }
        }
        .text
        .toString()
