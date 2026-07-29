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
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Which budgets the card lists, in what order, and where a row leads.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardBudgetsViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the budgets widget reads spend, limit and remainder off the active budget" {
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
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 400.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
        )

        val budgets = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().budgets
        budgets.size shouldBe 1
        budgets.first().name shouldBe "Groceries"
        budgets.first().spentFormatted shouldBe MoneyFormatter.format(100.dollars, USD)
        budgets.first().limitFormatted shouldBe MoneyFormatter.format(400.dollars, USD)
        budgets.first().remainderFormatted shouldBe MoneyFormatter.format(300.dollars, USD)
        budgets.first().progress shouldBe 0.25f
        // The fixture alerts at 80 %, which is where the bar's tick belongs.
        budgets.first().alertFraction shouldBe 0.8f
        budgets.first().status shouldBe BudgetStatus.OK
        budgets.first().isOver shouldBe false
    }

    "an overspent budget keeps a positive remainder and says it is over" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val spend = aTransaction(
            id = transactionId("tx-1"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.EXPENSE,
            money = 120.dollars,
            currencyCode = USD,
            operationDate = testDate,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(spend)),
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 100.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
        )

        val budget = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().budgets.first()
        // The sign lives in isOver — the row prints "20 over", never a negative remainder.
        budget.remainderFormatted shouldBe MoneyFormatter.format(20.dollars, USD)
        budget.isOver shouldBe true
        budget.status shouldBe BudgetStatus.OVER
        budget.progress shouldBe 1.2f
    }

    "the budgets widget lists the budgets nearest their limit first, capped at three" {
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
        // One spend of 100 against four general budgets: the smaller the cap, the tighter the budget.
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(spend)),
            budgets = listOf(
                budgetOf(ws, "b-loose", "Loose", 1000.dollars),
                budgetOf(ws, "b-tight", "Tight", 100.dollars),
                budgetOf(ws, "b-roomy", "Roomy", 500.dollars),
                budgetOf(ws, "b-snug", "Snug", 200.dollars),
            ),
        )

        val budgets = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().budgets
        budgets.map { it.name } shouldContainExactly listOf("Tight", "Snug", "Roomy")
    }

    "archived budgets never reach the budgets widget" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(id = budgetId("b-archived"), workspaceId = ws, isActive = false, startDate = testDate),
            ),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().budgets.shouldBeEmpty()
    }

    "tapping a budget row opens that budget, not the list" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.onEvent(DashboardEvent.OnBudgetClick(budgetId("b-1")))

        viewModel.sideEffects.effectFlow.first() shouldBe
            DashboardEffect.NavigateToBudgetDetails(budgetId("b-1"))
    }
})
