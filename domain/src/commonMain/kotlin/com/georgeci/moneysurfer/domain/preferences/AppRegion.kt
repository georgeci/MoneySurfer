package com.georgeci.moneysurfer.domain.preferences

/**
 * Region the user's money defaults come from — currency and date conventions.
 *
 * A closed shortlist rather than every ISO-3166 country: it mirrors the currency catalogue
 * (`CurrencyRepository`) one-for-one, so every region the picker offers has a currency the app can
 * actually show. It grows with that catalogue.
 */
enum class AppRegion {
    /** Follow the device region. */
    System,
    Germany,
    UnitedStates,
    UnitedKingdom,
    Poland,
    Ukraine,
    Georgia,
    Kazakhstan,
    Russia,

    ;

    companion object {
        val DEFAULT: AppRegion = System
    }
}
