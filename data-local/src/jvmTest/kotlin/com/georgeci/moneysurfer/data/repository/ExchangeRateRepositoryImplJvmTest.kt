package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.MutableClock
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.model.ExchangeRateTable
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRemoteSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Hands back whatever the test staged, and counts how often the repository asked. */
private class StubRemoteSource(var table: ExchangeRateTable?) : ExchangeRateRemoteSource {
    var calls: Int = 0
        private set

    override suspend fun fetchLatest(base: CurrencyCode): ExchangeRateTable? {
        calls++
        return table
    }
}

/**
 * The refresh policy — one fetch per base per day, and an unreachable provider leaves the cached
 * table alone — is the whole point of this class, and all of it is decided against what SQLite
 * reports as the last fetch time.
 */
class ExchangeRateRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var remote: StubRemoteSource
    lateinit var clock: MutableClock
    lateinit var repository: ExchangeRateRepositoryImpl

    beforeEach {
        database = inMemoryLocalDatabase()
        remote = StubRemoteSource(anExchangeRateTable())
        clock = MutableClock(testInstant)
        repository = ExchangeRateRepositoryImpl(
            dao = database.exchangeRateDao(),
            remoteSource = remote,
            clock = ClockUseCase(clock),
        )
    }

    afterEach { database.close() }

    "an empty cache reads as no table at all, not an empty one" {
        repository.observe(USD).first().shouldBeNull()
    }

    "the first refresh fetches and caches the provider's table" {
        repository.refreshIfStale(USD)

        remote.calls shouldBe 1
        repository.observe(USD).first() shouldBe anExchangeRateTable()
    }

    "a second refresh within the day does not hit the provider again" {
        repository.refreshIfStale(USD)
        clock.instant = testInstant + 23.hours + 59.minutes

        repository.refreshIfStale(USD)

        remote.calls shouldBe 1
    }

    "a refresh a day later fetches again" {
        repository.refreshIfStale(USD)
        clock.instant = testInstant + 24.hours

        repository.refreshIfStale(USD)

        remote.calls shouldBe 2
    }

    // Offline is an expected state on a phone, and the dashboard keeps showing the cached table
    // with its own "as of" date rather than blanking out.
    "an unreachable provider leaves the cached table in place" {
        repository.refreshIfStale(USD)
        clock.instant = testInstant + 25.hours
        remote.table = null

        repository.refreshIfStale(USD)

        repository.observe(USD).first() shouldBe anExchangeRateTable()
    }

    // Delete-then-insert, not upsert: a currency the provider stopped quoting has to disappear
    // instead of lingering at whatever rate it last had.
    "a refresh replaces the table rather than merging into it" {
        repository.refreshIfStale(USD)
        clock.instant = testInstant + 25.hours
        remote.table = anExchangeRateTable(rates = mapOf(EUR to 0.9), asOf = testInstant + 25.hours)

        repository.refreshIfStale(USD)

        val cached = repository.observe(USD).first()!!
        cached.rates shouldBe mapOf(EUR to 0.9)
        cached.asOf shouldBe testInstant + 25.hours
    }

    "each base currency keeps its own cache and its own staleness clock" {
        repository.refreshIfStale(USD)
        remote.table = anExchangeRateTable(base = EUR, rates = mapOf(RUB to 200.0))

        repository.refreshIfStale(EUR)

        remote.calls shouldBe 2
        repository.observe(USD).first()?.rates shouldBe mapOf(EUR to 0.5, RUB to 100.0)
        repository.observe(EUR).first()?.rates shouldBe mapOf(RUB to 200.0)
    }
})
