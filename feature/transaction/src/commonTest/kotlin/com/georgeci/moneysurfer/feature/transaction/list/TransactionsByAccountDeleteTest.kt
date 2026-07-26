package com.georgeci.moneysurfer.feature.transaction.list

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.transferId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Swipe-to-delete and its Undo on the transactions list (issue #363). The repository fake writes
 * for real, so what the list shows afterwards is the database's answer rather than an optimistic
 * guess the ViewModel made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsByAccountDeleteTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "swiping a row away removes it, and the snackbar's Undo puts it back" {
        runTest {
            val env = Env(transactions = listOf(expense(id = "rent", amount = 900), expense(id = "coffee", amount = 4)))
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnDeleteTransaction(transactionId("coffee")))

            // No optimistic removal in the ViewModel — the row is gone because the database Flow
            // re-emitted without it.
            viewModel.content().rowIds().shouldContainExactly("rent")

            env.snackbar.requests.first().onAction!!.invoke()

            viewModel.content().rowIds().shouldContainExactly("rent", "coffee")
        }
    }

    "a row that is already gone deletes nothing and offers no Undo" {
        runTest {
            val env = Env(transactions = listOf(expense(id = "rent", amount = 900)))
            val viewModel = env.viewModel()

            // Two devices, or a double tap: nothing was removed, so "Deleted · Undo" would be a
            // message about an event that did not happen.
            env.snackbar.requests.test {
                viewModel.onEvent(TransactionsByAccountEvent.OnDeleteTransaction(transactionId("ghost")))

                viewModel.content().rowIds().shouldContainExactly("rent")
                expectNoEvents()
            }
        }
    }

    "swiping one leg of a transfer takes both, and Undo restores the pair" {
        runTest {
            val transfer = transferId("tr-1")
            val env = Env(
                transactions = listOf(
                    expense(id = "rent", amount = 900),
                    expense(id = "leg-out", amount = 50).copy(transferId = transfer),
                    income(id = "leg-in", amount = 50).copy(transferId = transfer),
                ),
            )
            val viewModel = env.viewModel()

            // The leg the user swiped is the incoming one; the outgoing sibling has to go with it,
            // or the other account keeps money that no longer came from anywhere.
            viewModel.onEvent(TransactionsByAccountEvent.OnDeleteTransaction(transactionId("leg-in")))

            viewModel.content().rowIds().shouldContainExactly("rent")

            env.snackbar.requests.first().onAction!!.invoke()

            viewModel.content().rowIds().shouldHaveSize(3)
        }
    }
})
