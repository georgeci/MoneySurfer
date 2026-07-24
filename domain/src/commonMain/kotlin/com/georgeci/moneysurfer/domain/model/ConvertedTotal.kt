package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlin.time.Instant

/**
 * Every account balance folded into one figure in [baseCurrency], plus whatever could not get
 * there.
 *
 * The old dashboard total summed one currency and threw the rest away. The invariant that
 * replaces it: an account is either inside [total] or listed in [unconverted] — never neither.
 * A missing rate is a display problem, not a licence to drop money.
 */
data class ConvertedTotal(
    val baseCurrency: CurrencyCode,
    /** Converted sum, or `null` when not one account could be priced in [baseCurrency]. */
    val total: Money?,
    /** Per-currency totals for the accounts no rate covered, most-used currency first. */
    val unconverted: List<CurrencyTotal> = emptyList(),
    /**
     * Publication moment of the rates the conversion used, or `null` when it needed none (every
     * account was already in [baseCurrency]). Drives the "as of …" staleness signal.
     */
    val asOf: Instant? = null,
) {

    /** True when the headline figure accounts for every balance in the workspace. */
    val isComplete: Boolean get() = unconverted.isEmpty()
}
