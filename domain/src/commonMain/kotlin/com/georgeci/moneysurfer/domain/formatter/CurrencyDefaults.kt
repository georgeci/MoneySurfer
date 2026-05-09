package com.georgeci.moneysurfer.domain.formatter

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode

/**
 * Best-effort default ISO-4217 currency derived from the platform locale. Used as the seed
 * currency on first launch — the user can change it later via the currency picker (see mvp_1).
 *
 * Returns "USD" if the platform doesn't expose a currency for the current locale (e.g. a custom
 * locale, or the iOS simulator with `currencyCode == nil`).
 */
expect object CurrencyDefaults {
    fun systemDefault(): CurrencyCode
}

internal const val FALLBACK_CURRENCY: String = "USD"
