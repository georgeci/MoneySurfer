package com.georgeci.moneysurfer.feature.transaction.list

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * Where the transactions list sends the user, and what it carries along.
 *
 * The destinations are route arguments, not screen state, so nothing else in this module's specs
 * would notice an argument going missing — an add button that forgot the account it was pressed on,
 * or a filter screen counting today's period while the list shows January's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsByAccountNavigationTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "adding a transaction from an account's list prefills that account" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnAddTransactionClick)
                awaitItem() shouldBe TransactionsByAccountEffect.NavigateToTransactionCreation(ACCOUNT)
            }
        }
    }

    "the all-accounts list has no account to prefill" {
        runTest {
            val viewModel = Env().viewModel(accountId = null)

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnAddTransactionClick)
                awaitItem() shouldBe TransactionsByAccountEffect.NavigateToTransactionCreation(null)
            }
        }
    }

    "tapping a row opens that transaction, and Back leaves the list" {
        runTest {
            val viewModel = Env(listOf(expense("t-1", amount = 20))).viewModel()

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnTransactionClick(transactionId("t-1")))
                awaitItem() shouldBe
                    TransactionsByAccountEffect.NavigateToTransactionDetails(transactionId("t-1"))

                viewModel.onEvent(TransactionsByAccountEvent.OnBackClick)
                awaitItem() shouldBe TransactionsByAccountEffect.NavigateBack
            }
        }
    }

    "the filter screen is opened on the period the list is paged to, not on today's" {
        runTest {
            val viewModel = Env().viewModel()
            viewModel.content()

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)
            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnOpenFiltersClick)
                // Two months back from 2025-03-27, anchored on the month's first day; the filter
                // screen's live result count resolves its window from exactly this day, so
                // `Apply · N results` matches the list the user returns to.
                awaitItem() shouldBe TransactionsByAccountEffect.NavigateToFilters(
                    accountId = ACCOUNT,
                    anchorEpochDay = LocalDate(2025, 1, 1).toEpochDays(),
                )
            }
        }
    }

    "paging forward again returns to the period the list opened on" {
        runTest {
            val viewModel = Env().viewModel()
            viewModel.content()

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)
            viewModel.onEvent(TransactionsByAccountEvent.OnNextPeriodClick)

            viewModel.content().period shouldBe TransactionPeriodUi.Month(monthNumber = 3, year = 2025)
        }
    }

    "there is nowhere to page in all-time mode, so the anchor stays put" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()
            viewModel.content()
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            viewModel.content().period shouldBe TransactionPeriodUi.AllTime
            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnOpenFiltersClick)
                awaitItem() shouldBe TransactionsByAccountEffect.NavigateToFilters(
                    accountId = ACCOUNT,
                    anchorEpochDay = TODAY.toEpochDays(),
                )
            }
        }
    }

    "asking for another page when the window is exhausted changes nothing" {
        runTest {
            val viewModel = Env(List(3) { expense("t-$it", amount = 10) }).viewModel()
            val before = viewModel.content()
            before.canLoadMore shouldBe false

            viewModel.onEvent(TransactionsByAccountEvent.OnLoadMore)

            viewModel.content() shouldBe before
        }
    }
})
