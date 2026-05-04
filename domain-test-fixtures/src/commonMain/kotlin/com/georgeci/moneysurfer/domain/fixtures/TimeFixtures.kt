package com.georgeci.moneysurfer.domain.fixtures

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 2024-01-01T00:00:00Z — fixed clock anchor for tests. */
const val TEST_EPOCH_MILLIS: Long = 1_704_067_200_000L

val testInstant: Instant = Instant.fromEpochMilliseconds(TEST_EPOCH_MILLIS)
val testDate: LocalDate = LocalDate(2024, 1, 1)
