package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.db.dao.ExchangeRateDao
import com.georgeci.moneysurfer.data.db.entity.ExchangeRateEntity
import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRemoteSource
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Room-backed FX cache in front of [ExchangeRateRemoteSource].
 *
 * Refresh policy: at most one fetch per [REFRESH_INTERVAL] per base currency, triggered by whoever
 * observes the rates (in practice the dashboard, on open). Daily is the natural cadence — the
 * reference feeds this reads publish once a business day — and it keeps a backgrounded app that
 * gets resumed twenty times from making twenty requests.
 *
 * Everything else about failure is deliberately quiet: an unreachable provider leaves the previous
 * table in place, and the dashboard keeps showing it with its own "as of" date.
 */
@Single(binds = [ExchangeRateRepository::class])
class ExchangeRateRepositoryImpl(
    private val dao: ExchangeRateDao,
    private val remoteSource: ExchangeRateRemoteSource,
    private val clock: ClockUseCase,
) : ExchangeRateRepository {

    private val log = Logger.withTag(TAG)

    override fun observe(base: CurrencyCode): Flow<ExchangeRateTable?> =
        dao.observeByBase(base.value).map { rows -> rows.toTable(base) }

    override suspend fun refreshIfStale(base: CurrencyCode) {
        if (!isStale(base)) return
        val fetched = remoteSource.fetchLatest(base)
        if (fetched == null) {
            log.i { "[refresh] provider gave nothing for ${base.value} — keeping cache" }
            return
        }
        val fetchedAt = clock.now().toEpochMilliseconds()
        dao.replaceForBase(base.value, fetched.toEntities(fetchedAt))
        log.i { "[refresh] cached ${fetched.rates.size} rates for ${base.value} asOf=${fetched.asOf}" }
    }

    private suspend fun isStale(base: CurrencyCode): Boolean {
        val lastFetchedAt = dao.lastFetchedAt(base.value) ?: return true
        return clock.now() - Instant.fromEpochMilliseconds(lastFetchedAt) >= REFRESH_INTERVAL
    }

    private fun List<ExchangeRateEntity>.toTable(base: CurrencyCode): ExchangeRateTable? {
        if (isEmpty()) return null
        return ExchangeRateTable(
            baseCurrency = base,
            rates = associate { CurrencyCode(it.currency) to it.rate },
            // Every row of one fetch carries the same publication moment; the newest wins so a
            // half-written table (an interrupted replace) still reports the date it can back up.
            asOf = Instant.fromEpochMilliseconds(maxOf { it.asOf }),
        )
    }

    private fun ExchangeRateTable.toEntities(fetchedAt: Long): List<ExchangeRateEntity> =
        rates.map { (currency, rate) ->
            ExchangeRateEntity(
                baseCurrency = baseCurrency.value,
                currency = currency.value,
                rate = rate,
                asOf = asOf.toEpochMilliseconds(),
                fetchedAt = fetchedAt,
            )
        }

    private companion object {
        const val TAG = "ExchangeRateRepository"

        /** Staleness threshold: the reference feeds publish daily, so anything younger is current. */
        val REFRESH_INTERVAL: Duration = 24.hours
    }
}
