package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The week's pace, the month it projects, and the budget that judges it.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardBurnRateViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the burn rate formats the week's pace and the projection it implies" {
        val ws = workspaceId("ws-1")
        // testDate is 1 January 2024: day one of a 31-day month, so 30 days are still ahead.
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = FakeSpendAnalyticsRepository(
                daily = listOf(DailySpendPoint(date = testDate, total = 70.dollars)),
            ),
        )

        val burnRate = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().burnRate
        // 70 over seven days is 10 a day; 70 booked plus 30 days at 10 is 370 by month end.
        burnRate?.averageFormatted shouldBe MoneyFormatter.format(10.dollars, USD)
        burnRate?.projectedFormatted shouldBe MoneyFormatter.format(370.dollars, USD)
        burnRate?.weekTotalFormatted shouldBe MoneyFormatter.format(70.dollars, USD)
        burnRate?.days?.map { it.dayOfMonth } shouldContainExactly listOf(26, 27, 28, 29, 30, 31, 1)
        // Only the last bar is today, and it is the only day that booked anything.
        burnRate?.days?.map { it.isToday } shouldContainExactly listOf(
            false, false, false, false, false, false, true,
        )
        burnRate?.days?.map { it.fraction } shouldContainExactly listOf(0f, 0f, 0f, 0f, 0f, 0f, 1f)
        // Nothing caps the month, so the card draws the projection with no verdict on it.
        burnRate?.pace shouldBe null
    }

    "the burn rate is judged against a general monthly budget when there is one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 300.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
            spendAnalytics = FakeSpendAnalyticsRepository(
                daily = listOf(DailySpendPoint(date = testDate, total = 70.dollars)),
            ),
        )

        // A 370 projection against a 300 cap.
        viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
            .burnRate?.pace shouldBe BurnRatePace.OffPace
    }
})
