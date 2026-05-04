package com.georgeci.moneysurfer.domain.primitives

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    // --- arithmetic ---
    operator fun plus(other: Money): Money = Money(minor + other.minor)
    operator fun minus(other: Money): Money = Money(minor - other.minor)
    operator fun unaryMinus(): Money = Money(-minor)

    operator fun times(multiplier: Int): Money = Money(minor * multiplier)
    operator fun div(divisor: Int): Money = Money(minor / divisor)

    // --- compare ---
    override fun compareTo(other: Money): Int =
        minor.compareTo(other.minor)

    // --- utils ---
    fun isZero(): Boolean = minor == 0L
    fun isPositive(): Boolean = minor > 0
    fun isNegative(): Boolean = minor < 0

    fun abs(): Money = Money(kotlin.math.abs(minor))

    override fun toString(): String = "Money($minor)"

    companion object {
        const val MINOR_PER_MAJOR = 100.0
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
