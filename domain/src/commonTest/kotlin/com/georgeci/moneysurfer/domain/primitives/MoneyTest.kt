package com.georgeci.moneysurfer.domain.primitives

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MoneyTest : StringSpec({

    "plus that overflows Long throws instead of wrapping" {
        shouldThrow<ArithmeticException> {
            Money(Money.MAX_MINOR) + Money(Long.MAX_VALUE)
        }
        shouldThrow<ArithmeticException> {
            Money(Long.MAX_VALUE) + Money.fromMinor(1)
        }
    }

    "minus that overflows Long throws instead of wrapping" {
        shouldThrow<ArithmeticException> {
            Money(Long.MIN_VALUE) - Money.fromMinor(1)
        }
    }

    "negating Long.MIN_VALUE throws instead of wrapping back to itself" {
        shouldThrow<ArithmeticException> { -Money(Long.MIN_VALUE) }
    }

    "abs of Long.MIN_VALUE throws instead of returning a negative magnitude" {
        // Naive abs() wraps Long.MIN_VALUE back to Long.MIN_VALUE (still negative).
        shouldThrow<ArithmeticException> { Money(Long.MIN_VALUE).abs() }
    }

    "abs of an ordinary negative amount is its magnitude" {
        Money.fromMinor(-250).abs() shouldBe Money.fromMinor(250)
    }

    "times that overflows Long throws instead of wrapping" {
        shouldThrow<ArithmeticException> { Money(Long.MAX_VALUE) * 2 }
    }

    "negating Long.MIN_VALUE via times(-1) throws instead of wrapping" {
        shouldThrow<ArithmeticException> { Money(Long.MIN_VALUE) * -1 }
    }

    "times by zero is zero even for an out-of-cap magnitude" {
        (Money(Long.MAX_VALUE) * 0) shouldBe Money.zero()
    }

    "minus that overflows Long on the positive side throws instead of wrapping" {
        shouldThrow<ArithmeticException> { Money(Long.MAX_VALUE) - Money.fromMinor(-1) }
    }

    "fromMajor that overflows Long throws instead of wrapping" {
        shouldThrow<ArithmeticException> { Money.fromMajor(Long.MAX_VALUE) }
        shouldThrow<ArithmeticException> { Money.fromMajor(Long.MAX_VALUE / 100, cents = 99) }
    }

    "fromMajor composes units and cents exactly" {
        Money.fromMajor(12, cents = 34) shouldBe Money.fromMinor(1234)
    }

    "arithmetic within the domain cap stays exact" {
        (Money(Money.MAX_MINOR) + Money.fromMinor(-Money.MAX_MINOR)).minor shouldBe 0L
        (Money.fromMinor(300) - Money.fromMinor(100)) shouldBe Money.fromMinor(200)
    }
})
