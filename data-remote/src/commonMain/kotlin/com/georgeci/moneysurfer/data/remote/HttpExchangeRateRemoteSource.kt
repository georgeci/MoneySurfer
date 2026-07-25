package com.georgeci.moneysurfer.data.remote

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRemoteSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * Fetches the latest quotes over plain HTTP and decodes them into the domain table.
 *
 * Returns `null` for every failure mode — offline, timeout, 5xx, garbage payload — because the
 * caller's answer is the same in all of them: keep the cached table. Failures are logged, not
 * raised.
 */
@Single(binds = [ExchangeRateRemoteSource::class])
class HttpExchangeRateRemoteSource(
    private val config: ExchangeRateApiConfig,
) : ExchangeRateRemoteSource {

    private val log = Logger.withTag(TAG)

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetchLatest(base: CurrencyCode): ExchangeRateTable? {
        val url = config.latestUrl(base)
        return try {
            val body = httpGetText(url)
            json.decodeFromString(ExchangeRateLatestDto.serializer(), body).toDomain(base)
        } catch (cancellation: CancellationException) {
            // Leaving the screen mid-fetch is not a provider failure — let the cancellation
            // unwind instead of logging it and reporting "no rates".
            throw cancellation
        } catch (error: Throwable) {
            log.w(error) { "[fetch] $url failed — cached rates stand" }
            null
        }
    }

    private companion object {
        const val TAG = "ExchangeRateRemote"
    }
}
