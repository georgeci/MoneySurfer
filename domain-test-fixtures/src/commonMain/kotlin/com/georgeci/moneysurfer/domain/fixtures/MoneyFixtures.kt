package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money

val USD: CurrencyCode = CurrencyCode("USD")
val EUR: CurrencyCode = CurrencyCode("EUR")
val RUB: CurrencyCode = CurrencyCode("RUB")

/** Shorthand: `100.dollars` → `Money(10000)` minor units. */
val Int.dollars: Money get() = Money.fromMajor(toLong())
val Long.dollars: Money get() = Money.fromMajor(this)

/** Shorthand: `42.cents` → `Money(42)`. */
val Int.cents: Money get() = Money(toLong())
val Long.cents: Money get() = Money(this)
