package com.georgeci.moneysurfer.domain.primitives

import org.koin.core.annotation.Single
import kotlin.time.Instant
import kotlin.time.Clock as KotlinClock

/**
 * Domain-level clock abstraction. Wraps [kotlin.time.Clock.System] so callers don't
 * scatter direct `Clock.System.now()` references and so tests can substitute a fake
 * via Koin.
 *
 * The constructor parameter is for tests that need a fixed "now" — anything date-dependent, like
 * the transactions list's current period. Koin always builds the no-argument default.
 */
@Single
class ClockUseCase(private val clock: KotlinClock = KotlinClock.System) {
    fun now(): Instant = clock.now()
}
