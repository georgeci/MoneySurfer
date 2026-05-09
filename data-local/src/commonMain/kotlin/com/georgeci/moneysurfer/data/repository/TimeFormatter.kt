package com.georgeci.moneysurfer.data.repository

import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class TimeFormatter {

    fun parseInstant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

    fun parseInstantOrNull(epochMillis: Long?): Instant? =
        epochMillis?.let { Instant.fromEpochMilliseconds(it) }

    fun parseLocalDate(iso: String): LocalDate = LocalDate.parse(iso)

    fun parseLocalDateOrNull(iso: String): LocalDate? =
        if (iso.isBlank()) null else runCatching { LocalDate.parse(iso) }.getOrNull()

    fun formatInstant(instant: Instant): Long = instant.toEpochMilliseconds()

    fun formatInstantOrNull(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    fun formatLocalDate(date: LocalDate): String = date.toString()
}
