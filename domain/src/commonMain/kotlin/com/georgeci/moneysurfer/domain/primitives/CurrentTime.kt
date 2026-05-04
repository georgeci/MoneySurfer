package com.georgeci.moneysurfer.domain.primitives

import kotlin.time.Instant

expect fun currentTimeMillis(): Long

fun currentInstant(): Instant = Instant.fromEpochMilliseconds(currentTimeMillis())
