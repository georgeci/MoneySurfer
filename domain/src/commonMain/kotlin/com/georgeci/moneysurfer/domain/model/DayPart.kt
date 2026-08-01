package com.georgeci.moneysurfer.domain.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Which greeting a screen opens with. A device-local reading of the wall clock and nothing else —
 * it takes no workspace, no account and no network, so it is the one line of the dashboard toolbar
 * that is always true.
 *
 * The part of day rather than the sentence: "Good evening" needs a locale no view model has, so the
 * screen picks its copy off this. Same split as the upcoming card's "Today"/"Tomorrow" labels.
 */
enum class DayPart {
    Morning,
    Afternoon,
    Evening,
    Night,
    ;

    companion object {

        /**
         * The part of the day [instant] falls in, read in [timeZone].
         *
         * The boundaries are the conventional ones and deliberately not configurable: the greeting
         * is decoration, and someone up at 03:00 is better served by "Good night" than by a
         * setting. [Night] wraps midnight, so it is what the other three leave over rather than a
         * range of its own.
         */
        fun at(instant: Instant, timeZone: TimeZone): DayPart =
            when (instant.toLocalDateTime(timeZone).hour) {
                in MORNING_FROM_HOUR until AFTERNOON_FROM_HOUR -> Morning
                in AFTERNOON_FROM_HOUR until EVENING_FROM_HOUR -> Afternoon
                in EVENING_FROM_HOUR until NIGHT_FROM_HOUR -> Evening
                else -> Night
            }
    }
}

private const val MORNING_FROM_HOUR = 5
private const val AFTERNOON_FROM_HOUR = 12
private const val EVENING_FROM_HOUR = 17
private const val NIGHT_FROM_HOUR = 22
