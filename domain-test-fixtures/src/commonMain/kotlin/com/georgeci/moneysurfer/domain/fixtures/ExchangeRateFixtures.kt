package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Quotes are "units of [rates]'s key per one [base]" — see `ExchangeRateTable`. */
fun anExchangeRateTable(
    base: CurrencyCode = USD,
    rates: Map<CurrencyCode, Double> = mapOf(EUR to 0.5, RUB to 100.0),
    asOf: Instant = testInstant,
): ExchangeRateTable = ExchangeRateTable(baseCurrency = base, rates = rates, asOf = asOf)

/**
 * In-memory cache stand-in. [refreshCount] records the refresh calls so tests can assert the
 * observe path triggers exactly one, and [emit] simulates a fetch landing while a collector runs.
 */
class FakeExchangeRateRepository(
    seed: Map<CurrencyCode, ExchangeRateTable> = emptyMap(),
) : ExchangeRateRepository {

    private val state = MutableStateFlow(seed)

    var refreshCount: Int = 0
        private set

    override fun observe(base: CurrencyCode): Flow<ExchangeRateTable?> = state.map { it[base] }

    override suspend fun refreshIfStale(base: CurrencyCode) {
        refreshCount++
    }

    fun emit(table: ExchangeRateTable) {
        state.value = state.value + (table.baseCurrency to table)
    }
}
