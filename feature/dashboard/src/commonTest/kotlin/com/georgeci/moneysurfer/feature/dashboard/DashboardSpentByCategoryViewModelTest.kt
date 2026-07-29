package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
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
 * The per-category breakdown: its rows, their order, and the caps over them.
 *
 * Split out of `DashboardViewModelTest`: the dashboard composes every feature's numbers, so
 * one spec file for all of it grew past detekt's size limit and collided on every dashboard
 * PR. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardSpentByCategoryViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "spent-by-category stays empty with no spend, so the widget can say the month is bare" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.shouldBeEmpty()
    }

    "spent-by-category formats the aggregate's rows and orders them largest first" {
        val ws = workspaceId("ws-1")
        val rent = categoryId("c-rent")
        val food = categoryId("c-food")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            categories = listOf(
                aCategory(id = rent, workspaceId = ws, name = "Rent"),
                aCategory(id = food, workspaceId = ws, name = "Food"),
            ),
            spendAnalytics = spendInTestMonth(
                CategorySpendSlice(categoryId = food, total = 40.dollars, transactionCount = 2),
                CategorySpendSlice(categoryId = rent, total = 60.dollars, transactionCount = 1),
            ),
        )

        val rows = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory
        rows.map { it.name } shouldContainExactly listOf("Rent", "Food")
        rows.first().spentFormatted shouldBe MoneyFormatter.format(60.dollars, USD)
        rows.first().share shouldBe 0.6f
        rows.first().sharePercent shouldBe 60
        // Nothing caps either category, so the rows carry no over/near state to draw.
        rows.all { it.cap == null } shouldBe true
    }

    "a single-category budget reaches the row it caps as a formatted limit and a status" {
        val ws = workspaceId("ws-1")
        val food = categoryId("c-food")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(workspaceId = ws, categoryIds = listOf(food), amount = 100.dollars, startDate = testDate),
            ),
            categories = listOf(aCategory(id = food, workspaceId = ws, name = "Food")),
            spendAnalytics = spendInTestMonth(
                CategorySpendSlice(categoryId = food, total = 120.dollars, transactionCount = 3),
            ),
        )

        val cap = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.single().cap
        cap?.limitFormatted shouldBe MoneyFormatter.format(100.dollars, USD)
        cap?.status shouldBe BudgetStatus.OVER
        cap?.progress shouldBe 1.2f
    }

    "an uncategorized slice keeps its money and leaves the label to the screen" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendInTestMonth(
                CategorySpendSlice(categoryId = null, total = 40.dollars, transactionCount = 1),
            ),
        )

        val row = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.single()
        row.name shouldBe null
        row.categoryId shouldBe null
        row.hue shouldBe null
        row.spentFormatted shouldBe MoneyFormatter.format(40.dollars, USD)
    }
})
