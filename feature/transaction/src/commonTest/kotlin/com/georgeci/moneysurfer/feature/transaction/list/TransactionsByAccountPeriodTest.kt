package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * Period pager, windowed loading and period-scoped summary for `TransactionsByAccountViewModel`
 * (issue #261). The repository fake applies the window and the limit for real, so the assertions
 * are about what the ViewModel actually asked the database for. Filtering and search live in
 * [TransactionsByAccountFilteringTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsByAccountPeriodTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "opens on the month containing today" {
        runTest {
            val env = Env()

            val state = env.viewModel().content()

            state.periodMode shouldBe TransactionPeriodMode.Month
            state.period shouldBe TransactionPeriodUi.Month(monthNumber = 3, year = 2025)
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 3, 1),
                to = LocalDate(2025, 3, 31),
            )
        }
    }

    "only transactions inside the period reach the list" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "in-march", date = LocalDate(2025, 3, 10), amount = 30),
                    expense(id = "in-february", date = LocalDate(2025, 2, 10), amount = 40),
                ),
            )

            val state = env.viewModel().content()

            state.groups.flatMap { group -> group.transactions.map { it.id.value } }
                .shouldContainExactly("in-march")
        }
    }

    "the summary covers the period, not the loaded page" {
        runTest {
            val env = Env(
                transactions = listOf(
                    income(id = "salary", date = LocalDate(2025, 3, 1), amount = 100),
                    expense(id = "rent", date = LocalDate(2025, 3, 2), amount = 40),
                    expense(id = "old", date = LocalDate(2025, 1, 2), amount = 999),
                ),
            )

            val summary = env.viewModel().content().summary

            summary.incomeFormatted shouldBe "+$100.00"
            summary.expenseFormatted shouldBe "−$40.00"
            summary.netFormatted shouldBe "+$60.00"
            summary.netPositive shouldBe true
        }
    }

    "paging back a month re-queries the previous window" {
        runTest {
            val env = Env(
                transactions = listOf(expense(id = "february", date = LocalDate(2025, 2, 10), amount = 30)),
            )
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.Month(monthNumber = 2, year = 2025)
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 2, 1),
                to = LocalDate(2025, 2, 28),
            )
            state.groups.single().transactions.single().id.value shouldBe "february"
        }
    }

    "the pager stops at the current period but always allows going back" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.content().canGoToNextPeriod shouldBe false
            viewModel.content().canGoToPreviousPeriod shouldBe true

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            viewModel.content().canGoToNextPeriod shouldBe true
        }
    }

    "week mode windows Monday to Sunday and labels the ISO week" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.Week),
            )

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.Week(
                from = LocalDate(2025, 3, 24),
                to = LocalDate(2025, 3, 30),
                weekNumber = 13,
                weekYear = 2025,
            )
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 3, 24),
                to = LocalDate(2025, 3, 30),
            )
        }
    }

    "the period mode is persisted, not held in the screen state" {
        runTest {
            val env = Env()

            env.viewModel().onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            env.preferences.storedPeriodMode() shouldBe TransactionPeriodMode.AllTime
        }
    }

    "all time drops the date bounds but stays paged" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()

            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.AllTime
            state.canGoToPreviousPeriod shouldBe false
            state.canGoToNextPeriod shouldBe false
            env.repository.lastWindow shouldBe TransactionPeriodWindow.Unbounded
            state.rowCount() shouldBe PAGE_SIZE
            state.canLoadMore shouldBe true
        }
    }

    "loading more appends the next page and stops when the window is exhausted" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.onEvent(TransactionsByAccountEvent.OnLoadMore)

            val state = viewModel.content()
            state.rowCount() shouldBe PAGE_SIZE + 5
            state.canLoadMore shouldBe false
        }
    }

    "changing the period resets paging back to the first page" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )
            viewModel.onEvent(TransactionsByAccountEvent.OnLoadMore)

            viewModel.onEvent(TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.Month))
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.content().rowCount() shouldBe PAGE_SIZE
        }
    }

    "day headers are relative for today and yesterday and exact otherwise" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "now", date = TODAY, amount = 1),
                    expense(id = "then", date = LocalDate(2025, 3, 26), amount = 1),
                    expense(id = "older", date = LocalDate(2025, 3, 10), amount = 1),
                ),
            )

            val labels = env.viewModel().content().groups.map { it.dateLabel }

            labels[0] shouldBe TransactionDateUi.Today
            labels[1] shouldBe TransactionDateUi.Yesterday
            labels[2] shouldBe TransactionDateUi.Exact(LocalDate(2025, 3, 10))
        }
    }

    "the all-accounts summary currency is the dominant one, not the newest row's" {
        runTest {
            val env = Env(
                transactions = listOf(
                    // Newest, but the smaller total: it must not decide the summary currency.
                    expense(id = "usd", date = LocalDate(2025, 3, 20), amount = 10, currency = USD),
                    expense(id = "eur", date = LocalDate(2025, 3, 1), amount = 30, currency = EUR),
                ),
            )

            val summary = env.viewModel(accountId = null).content().summary

            summary.expenseFormatted shouldBe "−€30.00"
        }
    }
})
