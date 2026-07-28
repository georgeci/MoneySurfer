package com.georgeci.moneysurfer.domain.preferences

/**
 * First day of the week for calendar views and week-scoped periods.
 *
 * No `System` entry: neither `kotlinx-datetime` nor the common source set exposes the locale's
 * first day of week, so a `System` option would have nothing to resolve against and would show the
 * user a value the app cannot name.
 */
enum class WeekStart {
    Monday,
    Saturday,
    Sunday,

    ;

    companion object {
        val DEFAULT: WeekStart = Monday
    }
}
