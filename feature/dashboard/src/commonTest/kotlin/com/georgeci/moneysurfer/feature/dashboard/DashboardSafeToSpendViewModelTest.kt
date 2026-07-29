package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * What the safe-to-spend headline reads off the active budgets.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardSafeToSpendViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "safe-to-spend stays null with no budget, so the widget can offer to set one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend shouldBe null
    }

    "safe-to-spend reads the active budget's remainder, pace and days left" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val spend = aTransaction(
            id = transactionId("tx-1"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.EXPENSE,
            money = 100.dollars,
            currencyCode = USD,
            operationDate = testDate,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(spend)),
            // A January budget of 310 against 100 spent on its first day: 210 left over 31 days.
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 310.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
        )

        val safeToSpend = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend
        safeToSpend?.remainingFormatted shouldBe MoneyFormatter.format(210.dollars, USD)
        safeToSpend?.perDayFormatted shouldBe MoneyFormatter.format(Money(677), USD)
        safeToSpend?.daysLeft shouldBe 31
        // Day one of the window: none of it is behind us yet, so the pace tick sits at the start.
        safeToSpend?.paceFraction shouldBe 0f
        safeToSpend?.status shouldBe BudgetStatus.OK
        safeToSpend?.isOver shouldBe false
    }

    "safe-to-spend ignores archived budgets" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(id = budgetId("b-archived"), workspaceId = ws, isActive = false, startDate = testDate),
            ),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend shouldBe null
    }
})
