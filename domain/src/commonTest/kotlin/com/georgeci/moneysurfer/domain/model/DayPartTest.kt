package com.georgeci.moneysurfer.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

/**
 * The greeting's one decision: which part of the day a moment falls in, read on the device's own
 * clock. Every hour is covered rather than the four midpoints — the only bug this can carry is an
 * off-by-one at a boundary, and a midpoint never sees one.
 */
class DayPartTest : StringSpec({

    val utc = TimeZone.UTC

    fun dayPartAt(hour: Int, minute: Int = 0, zone: TimeZone = utc): DayPart =
        DayPart.at(LocalDateTime(2026, 7, 15, hour, minute).toInstant(utc), zone)

    "every hour of the day resolves to exactly one greeting" {
        (0..23).map { dayPartAt(it) } shouldBe listOf(
            // 00:00–04:59 — the wrap of the night that started the evening before.
            DayPart.Night, DayPart.Night, DayPart.Night, DayPart.Night, DayPart.Night,
            // 05:00–11:59
            DayPart.Morning, DayPart.Morning, DayPart.Morning, DayPart.Morning,
            DayPart.Morning, DayPart.Morning, DayPart.Morning,
            // 12:00–16:59
            DayPart.Afternoon, DayPart.Afternoon, DayPart.Afternoon, DayPart.Afternoon,
            DayPart.Afternoon,
            // 17:00–21:59
            DayPart.Evening, DayPart.Evening, DayPart.Evening, DayPart.Evening, DayPart.Evening,
            // 22:00–23:59
            DayPart.Night, DayPart.Night,
        )
    }

    "the last minute of a part still belongs to it" {
        dayPartAt(4, 59) shouldBe DayPart.Night
        dayPartAt(11, 59) shouldBe DayPart.Morning
        dayPartAt(16, 59) shouldBe DayPart.Afternoon
        dayPartAt(21, 59) shouldBe DayPart.Evening
    }

    "the reading is local, not UTC — one instant greets two zones differently" {
        // 20:00 UTC is the evening nine time zones west of the device that reads it as breakfast.
        // Reading the instant rather than the wall clock would wish that user a good evening at 05:00.
        dayPartAt(20) shouldBe DayPart.Evening
        dayPartAt(20, zone = FixedOffsetTimeZone(UtcOffset(hours = 9))) shouldBe DayPart.Morning
    }
})
