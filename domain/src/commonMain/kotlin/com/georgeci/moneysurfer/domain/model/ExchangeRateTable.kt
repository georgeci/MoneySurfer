package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * One FX quote table, all rates expressed against a single [baseCurrency].
 *
 * [rates] holds *units of the quoted currency per one unit of [baseCurrency]* — the shape every
 * mainstream provider publishes (`base=USD` → `{"EUR": 0.92}` means 1 USD buys 0.92 EUR). Turning
 * a foreign balance into the base therefore divides rather than multiplies; see [convertToBase].
 *
 * [asOf] is the provider's own publication moment, not when the app fetched it — ECB-style feeds
 * only publish on business days, so a table fetched this morning can legitimately be two days old
 * and the UI has to be able to say so.
 */
data class ExchangeRateTable(
    val baseCurrency: CurrencyCode,
    val rates: Map<CurrencyCode, Double>,
    val asOf: Instant,
) {

    /**
     * Quote for [currency], or `null` when this table cannot price it.
     *
     * The base currency always quotes at 1 even when the provider omits it from its own payload
     * (most do), and non-positive or non-finite quotes are treated as absent rather than trusted
     * into a division — a zero rate from a malformed payload would otherwise produce an infinite
     * balance.
     */
    fun rateFor(currency: CurrencyCode): Double? {
        if (currency == baseCurrency) return 1.0
        val rate = rates[currency] ?: return null
        return rate.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * [money], denominated in [currency], expressed in [baseCurrency]; `null` when no rate covers
     * [currency] and the caller has to surface the amount separately instead of dropping it.
     *
     * Rounds to the nearest minor unit (half away from zero). Money stays integral `Long` minor
     * units — the `Double` only ever appears inside this one division, and balances are capped at
     * [Money.MAX_MINOR], four orders of magnitude below the point where a `Double` stops
     * representing whole minor units exactly.
     *
     * A quote small enough to push the quotient past [Long] also counts as "cannot price it":
     * `roundToLong` saturates rather than throwing, so an absurd rate from a malformed payload
     * would otherwise turn into a silently clamped balance and then blow up the overflow-checked
     * sum. Reporting it as unconverted keeps that amount visible and the total honest.
     */
    fun convertToBase(money: Money, currency: CurrencyCode): Money? {
        val rate = rateFor(currency) ?: return null
        if (rate == 1.0) return money
        return (money.minor / rate)
            .takeIf { it.isFinite() && abs(it) <= Money.MAX_MINOR }
            ?.let { Money(it.roundToLong()) }
    }
}

/** The base currency in force plus the newest rates cached for it, if any have been cached yet. */
data class ExchangeRateSnapshot(
    val baseCurrency: CurrencyCode,
    val rates: ExchangeRateTable?,
)
