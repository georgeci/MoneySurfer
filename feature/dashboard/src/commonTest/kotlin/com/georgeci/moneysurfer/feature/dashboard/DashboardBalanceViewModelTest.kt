package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeExchangeRateRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.BalanceTrend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.primitives.Money
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
import kotlinx.datetime.yearMonth

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

    "the month delta is what this month netted, signed and formatted" {
        val viewModel = newViewModel(
            ws = WS,
            accounts = accountsOf(1_000.dollars),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = netsOf(monthNet(income = 300.dollars, expense = 100.dollars)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTrendDelta shouldBe "+${MoneyFormatter.format(200.dollars, USD)}"
        content.isTrendDeltaNegative shouldBe false
    }

    "a month that spent more than it earned reads as a fall" {
        val viewModel = newViewModel(
            ws = WS,
            accounts = accountsOf(1_000.dollars),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = netsOf(monthNet(income = 100.dollars, expense = 400.dollars)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        // Locale formatting carries the minus itself; nothing prepends a second sign.
        content.formattedTrendDelta shouldBe MoneyFormatter.format((-300).dollars, USD)
        content.isTrendDeltaNegative shouldBe true
    }

    "the curve ends on the headline total and walks back one month at a time" {
        val viewModel = newViewModel(
            ws = WS,
            accounts = accountsOf(1_000.dollars),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = netsOf(monthNet(income = 300.dollars, expense = 100.dollars)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.balanceSeries.size shouldBe BalanceTrend.TREND_MONTHS
        // Major units, and only this month booked anything: five flat months, then +200.
        content.balanceSeries shouldContainExactly listOf(800f, 800f, 800f, 800f, 800f, 1_000f)
    }

    "a window that booked nothing draws no curve and states no delta" {
        val viewModel = newViewModel(
            ws = WS,
            accounts = accountsOf(1_000.dollars),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        // Six repeats of the present balance is a claim about months the aggregate cannot see —
        // opening balances are outside it — so the widget is told there is nothing to draw.
        content.formattedTrendDelta shouldBe null
        content.balanceSeries.shouldBeEmpty()
    }

    "a headline a rate helped build carries no trend rather than one that omits the foreign half" {
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
            rates = FakeExchangeRateRepository(mapOf(USD to anExchangeRateTable())),
            spendAnalytics = netsOf(monthNet(income = 300.dollars)),
        )

        // The EUR balance is inside the headline but its movements are outside the aggregate, which
        // filters on the base currency — so a delta folded from those nets would report the USD half
        // of the month as if it were all of it.
        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(200.dollars, USD)
        content.formattedTrendDelta shouldBe null
        content.balanceSeries.shouldBeEmpty()
    }

    "a currency left unconverted beside the headline also stands the trend down" {
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
            // No rates cached, so the EUR balance is listed beside the headline instead of folded in.
            rates = FakeExchangeRateRepository(),
            spendAnalytics = netsOf(monthNet(income = 300.dollars)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.otherCurrencyTotals shouldContainExactly listOf(MoneyFormatter.format(50.dollars, EUR))
        content.formattedTrendDelta shouldBe null
        content.balanceSeries.shouldBeEmpty()
    }

    "a headline that fell back to an unconvertible bucket carries no trend" {
        val viewModel = newViewModel(
            ws = WS,
            accounts = accountsOf(1_000.dollars),
            transactions = FakeTransactionRepository(emptyList()),
            // Base-currency history, against a headline that is one foreign bucket instead of a
            // converted total: the curve would belong to neither figure.
            baseCurrency = EUR,
            spendAnalytics = netsOf(monthNet(income = 300.dollars)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(1_000.dollars, USD)
        content.formattedTrendDelta shouldBe null
        content.balanceSeries.shouldBeEmpty()
    }
})

private val WS = workspaceId("ws-1")

/** One USD account holding [balance] — the simplest workspace a trend can be anchored on. */
private fun accountsOf(balance: Money) = FakeAccountRepository(
    listOf(anAccount(id = accountId("a-1"), workspaceId = WS, currencyCode = USD, balance = balance)),
)

/**
 * A net for the month the shared test clock sits in, which is the newest month of the trend window
 * and therefore the one the delta is read off.
 */
private fun monthNet(income: Money = Money.zero(), expense: Money = Money.zero()) =
    MonthlyNet(month = testDate.yearMonth, income = income, expense = expense)

private fun netsOf(vararg nets: MonthlyNet) = FakeSpendAnalyticsRepository(nets = nets.toList())
