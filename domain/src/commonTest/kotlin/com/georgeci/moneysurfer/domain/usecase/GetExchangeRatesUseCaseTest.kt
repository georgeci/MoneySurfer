package com.georgeci.moneysurfer.domain.usecase

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeExchangeRateRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class GetExchangeRatesUseCaseTest : StringSpec({

    "emits null while no workspace is selected" {
        val useCase = GetExchangeRatesUseCase(
            session = InMemorySessionPointers(),
            workspaceRepository = FakeGoalWorkspaceRepository(),
            exchangeRates = FakeExchangeRateRepository(),
        )

        runTest {
            useCase().test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "pairs the workspace base currency with the cached table and asks for a refresh" {
        val ws = workspaceId("ws-1")
        val rates = FakeExchangeRateRepository(mapOf(USD to anExchangeRateTable()))
        val useCase = GetExchangeRatesUseCase(
            session = InMemorySessionPointers(currentWorkspaceId = ws),
            workspaceRepository = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, baseCurrency = USD))),
            exchangeRates = rates,
        )

        runTest {
            useCase().test {
                val snapshot = awaitItem()
                snapshot?.baseCurrency shouldBe USD
                snapshot?.rates shouldBe anExchangeRateTable()
                rates.refreshCount shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "an empty cache still emits, so the dashboard renders before the first fetch lands" {
        val ws = workspaceId("ws-1")
        val rates = FakeExchangeRateRepository()
        val useCase = GetExchangeRatesUseCase(
            session = InMemorySessionPointers(currentWorkspaceId = ws),
            workspaceRepository = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, baseCurrency = EUR))),
            exchangeRates = rates,
        )

        runTest {
            useCase().test {
                val snapshot = awaitItem()
                snapshot?.baseCurrency shouldBe EUR
                snapshot?.rates.shouldBeNull()

                // A fetch landing later reaches the collector without it re-subscribing.
                rates.emit(anExchangeRateTable(base = EUR, rates = mapOf(USD to 1.1)))
                awaitItem()?.rates?.rates shouldBe mapOf(USD to 1.1)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
