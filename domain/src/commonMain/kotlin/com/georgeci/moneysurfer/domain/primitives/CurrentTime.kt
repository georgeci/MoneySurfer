package com.georgeci.moneysurfer.domain.primitives

import org.koin.core.annotation.Single
import kotlin.time.Clock as KotlinClock
import kotlin.time.Instant

/**
 * Domain-level clock abstraction. Wraps [kotlin.time.Clock.System] so callers don't
 * scatter direct `Clock.System.now()` references and so tests can substitute a fake
 * via Koin.
 */
@Single
class Clock {
    fun now(): Instant = KotlinClock.System.now()
    fun nowMillis(): Long = now().toEpochMilliseconds()
}
