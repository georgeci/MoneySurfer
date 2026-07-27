package com.georgeci.moneysurfer.domain.preferences

/** Clock convention used when a time of day is written out. */
enum class HourFormat {
    /** Follow the device's 12/24-hour setting. */
    System,
    TwelveHour,
    TwentyFourHour,

    ;

    companion object {
        val DEFAULT: HourFormat = System
    }
}
