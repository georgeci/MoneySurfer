package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The Week/Month switch: what it opens on, what it re-picks, when it stands down.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardPeriodViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the dashboard opens on Month with the switch shown over the default layout" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.period shouldBe DashboardPeriod.Month
        content.periodSwitchVisible shouldBe true
    }

    "picking a period re-picks the budget the headline speaks for" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(
                    id = budgetId("b-weekly"),
                    workspaceId = ws,
                    name = "This week",
                    amount = 200.dollars,
                    period = BudgetPeriod.WEEKLY,
                    categoryIds = emptyList(),
                    startDate = testDate,
                ),
                aBudget(
                    id = budgetId("b-monthly"),
                    workspaceId = ws,
                    name = "This month",
                    amount = 800.dollars,
                    period = BudgetPeriod.MONTHLY,
                    categoryIds = emptyList(),
                    startDate = testDate,
                ),
            ),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
            .safeToSpend?.budgetName shouldBe "This month"

        viewModel.onEvent(DashboardEvent.OnPeriodChange(DashboardPeriod.Week))

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.period shouldBe DashboardPeriod.Week
        content.safeToSpend?.budgetName shouldBe "This week"
    }

    "the period switch stands down when nothing on the layout reads it" {
        val ws = workspaceId("ws-1")
        val layout = DashboardWidgetType.entries
            .filter { it.isPeriodScoped }
            .fold(DashboardLayoutConfig.DEFAULT) { acc, type -> acc.withWidgetEnabled(type, enabled = false) }
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            uiPreferences = FakeUiPreferences(dashboardLayout = layout),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().periodSwitchVisible shouldBe false
    }
})
