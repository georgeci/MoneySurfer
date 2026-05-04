package com.georgeci.moneysurfer.data.repository

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
internal fun Long?.toInstantOrNull(): Instant? = this?.let { Instant.fromEpochMilliseconds(it) }
internal fun Instant.toEpochMillis(): Long = toEpochMilliseconds()
internal fun Instant?.toEpochMillisOrNull(): Long? = this?.toEpochMilliseconds()
internal fun String.toLocalDate(): LocalDate = LocalDate.parse(this)
internal fun String.toLocalDateOrNull(): LocalDate? =
    if (isBlank()) null else runCatching { LocalDate.parse(this) }.getOrNull()
internal fun LocalDate.toIsoDate(): String = toString()
