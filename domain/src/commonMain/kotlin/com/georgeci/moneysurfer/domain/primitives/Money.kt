package com.georgeci.moneysurfer.domain.primitives

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    // --- arithmetic ---
    // Checked: a crafted amount (see CSV import, issue #159) must never silently
    // wrap the cached account balance. Overflow throws rather than corrupting the
    // total. In practice values are capped at [MAX_MINOR] on entry, so honest
    // balances never come close to the Long limit and these never throw.
    operator fun plus(other: Money): Money = Money(addExact(minor, other.minor))
    operator fun minus(other: Money): Money = Money(subtractExact(minor, other.minor))
    operator fun unaryMinus(): Money = Money(negateExact(minor))

    operator fun times(multiplier: Int): Money = Money(multiplyExact(minor, multiplier.toLong()))
    operator fun div(divisor: Int): Money = Money(minor / divisor)

    // --- compare ---
    override fun compareTo(other: Money): Int =
        minor.compareTo(other.minor)

    // --- utils ---
    fun isZero(): Boolean = minor == 0L
    fun isPositive(): Boolean = minor > 0
    fun isNegative(): Boolean = minor < 0

    // negateExact guards Long.MIN_VALUE, whose naive abs() wraps back to itself.
    fun abs(): Money = if (minor < 0) Money(negateExact(minor)) else this

    override fun toString(): String = "Money($minor)"

    companion object {
        const val MINOR_PER_MAJOR = 100.0

        /**
         * Domain cap on `|minor|`: 10^15 minor units (10 trillion major). Larger
         * than any honest balance yet small enough that summing many rows stays
         * well inside [Long]. Enforced at the CSV import boundary so a crafted
         * `amount_minor` (e.g. [Long.MIN_VALUE]) can never enter the domain and
         * wrap a cached balance. See issue #159.
         */
        const val MAX_MINOR: Long = 1_000_000_000_000_000L

        private const val CENTS_RANGE_MAX = 99
        private const val MINOR_FACTOR = 100

        fun fromMinor(minor: Long): Money = Money(minor)

        fun fromMajor(
            units: Long,
            cents: Int = 0,
        ): Money {
            require(cents in 0..CENTS_RANGE_MAX)
            return Money(units * MINOR_FACTOR + cents)
        }

        fun fromDouble(value: Double): Money {
            return Money((value * MINOR_FACTOR).roundToLong())
        }

        fun zero(): Money = Money(0)
    }
}

// Overflow-checked Long arithmetic (Math.*Exact is JVM-only; Money is common
// KMP code). Each mirrors the JDK algorithm and throws on overflow instead of
// wrapping. Kept file-private to the Money value type.

private const val LONG_HALF_BITS = 32

private fun addExact(a: Long, b: Long): Long {
    val r = a + b
    // Overflow iff the operands share a sign that the result does not.
    if ((a xor r) and (b xor r) < 0L) {
        throw ArithmeticException("Money overflow: $a + $b")
    }
    return r
}

private fun subtractExact(a: Long, b: Long): Long {
    val r = a - b
    // Overflow iff the operands differ in sign and the result takes b's sign.
    if ((a xor b) and (a xor r) < 0L) {
        throw ArithmeticException("Money overflow: $a - $b")
    }
    return r
}

private fun negateExact(a: Long): Long {
    if (a == Long.MIN_VALUE) {
        throw ArithmeticException("Money overflow: -($a)")
    }
    return -a
}

private fun multiplyExact(a: Long, b: Long): Long {
    val r = a * b
    val absHigh = (if (a < 0) -a else a) or (if (b < 0) -b else b)
    // Fast path: when both magnitudes fit in 31 bits the product cannot overflow.
    if (absHigh ushr (LONG_HALF_BITS - 1) == 0L) return r
    val divisionMismatch = b != 0L && r / b != a
    val minTimesNegativeOne = a == Long.MIN_VALUE && b == -1L
    if (divisionMismatch || minTimesNegativeOne) {
        throw ArithmeticException("Money overflow: $a * $b")
    }
    return r
}
