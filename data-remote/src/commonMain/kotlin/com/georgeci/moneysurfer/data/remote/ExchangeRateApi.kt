package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Where the FX quotes come from.
 *
 * Default is ExchangeRate-API's *open* endpoint (`open.er-api.com`): free, no API key, no
 * registration, ~160 currencies, refreshed daily. Its free tier asks for attribution wherever the
 * rates are shown, which the dashboard's "as of" footnote carries.
 *
 * Bound in `RemoteDataModule` so the URL is one override away — swapping providers means replacing
 * this binding and [ExchangeRateLatestDto], nothing above the data layer.
 */
data class ExchangeRateApiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** `…/v6/latest/USD` — the base currency is a path segment, so nothing needs URL-encoding. */
    fun latestUrl(base: CurrencyCode): String = "$baseUrl/${base.value}"

    companion object {
        const val DEFAULT_BASE_URL: String = "https://open.er-api.com/v6/latest"
    }
}

/**
 * The subset of the provider's payload that matters. Unknown fields (`provider`, `terms_of_use`,
 * `time_next_update_*`, …) are ignored by the decoder rather than modelled.
 */
@Serializable
internal data class ExchangeRateLatestDto(
    val result: String = "",
    @SerialName("base_code") val baseCode: String = "",
    @SerialName("time_last_update_unix") val timeLastUpdateUnix: Long = 0L,
    val rates: Map<String, Double> = emptyMap(),
)

/**
 * Maps the payload to the domain table, or `null` when it is not usable: a non-success `result`,
 * an empty quote set, a base that is not the one asked for (a redirect or a provider fallback), or
 * a missing publication timestamp — all of which would otherwise cache a table the UI would
 * mislabel.
 */
internal fun ExchangeRateLatestDto.toDomain(requested: CurrencyCode): ExchangeRateTable? {
    if (result != RESULT_SUCCESS || rates.isEmpty() || timeLastUpdateUnix <= 0L) return null
    if (!baseCode.equals(requested.value, ignoreCase = true)) return null
    return ExchangeRateTable(
        baseCurrency = requested,
        rates = rates
            .filterValues { it.isFinite() && it > 0.0 }
            .mapKeys { (code, _) -> CurrencyCode(code.uppercase()) },
        asOf = Instant.fromEpochSeconds(timeLastUpdateUnix),
    )
}

private const val RESULT_SUCCESS = "success"
