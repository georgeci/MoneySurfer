package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import kotlinx.coroutines.flow.Flow

/**
 * Locally cached FX quotes. The cache is the read path — the network only ever refills it — so
 * the dashboard total renders offline from whatever was fetched last.
 */
interface ExchangeRateRepository {

    /**
     * Newest cached table quoted against [base], emitting again whenever a refresh lands.
     * `null` until the first successful fetch for that base.
     */
    fun observe(base: CurrencyCode): Flow<ExchangeRateTable?>

    /**
     * Refetches [base] when the cached copy is older than the staleness threshold, and does
     * nothing otherwise. Never throws: a failed refresh leaves the cache — stale or empty —
     * exactly as it was, because a total from yesterday's rates beats no total at all.
     */
    suspend fun refreshIfStale(base: CurrencyCode)
}
