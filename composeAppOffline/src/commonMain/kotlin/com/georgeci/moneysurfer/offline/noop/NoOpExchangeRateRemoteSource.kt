package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRemoteSource

/**
 * The offline build has no network layer at all, so the FX cache is never filled. The dashboard
 * total then covers the base-currency accounts and lists the rest beside it — the same graceful
 * path the online build takes before its first successful fetch.
 */
class NoOpExchangeRateRemoteSource : ExchangeRateRemoteSource {
    override suspend fun fetchLatest(base: CurrencyCode): ExchangeRateTable? = null
}
