package com.georgeci.moneysurfer.domain.preferences

/**
 * UI language the user picked for the app, independent of the device language.
 *
 * The entries are the locales the app actually ships translations for — `values/` and
 * `values-ru/`. Adding a translation means adding an entry here, not a free-form locale tag: a
 * stored `fr-CA` the app cannot render would leave the picker showing a language nothing else
 * honours.
 */
enum class AppLanguage {
    /** Follow the device language — the only value that is right on a device we do not translate. */
    System,
    English,
    Russian,

    ;

    companion object {
        val DEFAULT: AppLanguage = System
    }
}
