package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode

/**
 * The FX provider behind [ExchangeRateRepository]. Implemented over HTTP in `data-remote`; the
 * offline build binds a no-op so the cache is simply never refilled there.
 */
interface ExchangeRateRemoteSource {

    /**
     * Latest published quotes against [base], or `null` when the provider could not be reached
     * or answered with something unusable. Implementations do not throw — an unreachable
     * provider is an expected state on a phone, not an error the caller can act on.
     */
    suspend fun fetchLatest(base: CurrencyCode): ExchangeRateTable?
}
