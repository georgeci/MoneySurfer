package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeExchangeRateRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The headline total, and the currencies no cached rate could fold into it.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardBalanceViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the headline total folds every currency into the workspace base currency" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                    // 0.5 EUR per USD → 50 EUR is another 100 USD
                    anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR, balance = 50.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            rates = FakeExchangeRateRepository(mapOf(USD to anExchangeRateTable())),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(200.dollars, USD)
        content.otherCurrencyTotals.shouldBeEmpty()
        content.ratesAsOf shouldBe "2024-01-01"
    }

    "a currency no cached rate covers is shown beside the headline, never dropped" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                    anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR, balance = 50.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            // Nothing cached yet — offline first run, or the offline build.
            rates = FakeExchangeRateRepository(),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(100.dollars, USD)
        content.otherCurrencyTotals shouldContainExactly listOf(MoneyFormatter.format(50.dollars, EUR))
        content.ratesAsOf shouldBe null
    }

    "with nothing priceable in the base currency the headline is still a balance, not the empty state" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            baseCurrency = EUR,
            rates = FakeExchangeRateRepository(),
        )

        // A null headline is the screen's "no accounts at all" signal — an unconvertible
        // balance must not trip it.
        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(100.dollars, USD)
        content.otherCurrencyTotals.shouldBeEmpty()
    }
})
