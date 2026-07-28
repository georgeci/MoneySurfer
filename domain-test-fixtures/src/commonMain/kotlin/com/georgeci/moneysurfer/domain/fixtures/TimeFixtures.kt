package com.georgeci.moneysurfer.domain.fixtures

import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** 2024-01-01T00:00:00Z — fixed clock anchor for tests. */
const val TEST_EPOCH_MILLIS: Long = 1_704_067_200_000L

val testInstant: Instant = Instant.fromEpochMilliseconds(TEST_EPOCH_MILLIS)
val testDate: LocalDate = LocalDate(2024, 1, 1)

/** A [Clock] frozen at [instant] — wrap in `ClockUseCase(...)` for anything date-dependent. */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * A [Clock] the test moves by hand. For code that re-derives a date over time — pair it with
 * `runTest`'s virtual clock so the delay and the reading advance together.
 */
class MutableClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}
