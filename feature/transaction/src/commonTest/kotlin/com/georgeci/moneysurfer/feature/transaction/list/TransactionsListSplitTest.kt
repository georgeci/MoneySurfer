package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.splitId
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * One receipt, one row.
 *
 * The legs of a split are ordinary transactions everywhere else — that is the point of the shape —
 * so the list is where they have to be put back together. The risk the cases below guard is a
 * collapsed row showing a total that is quietly missing the legs the page did not load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsListSplitTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    val receipt = splitId("sp-1")

    fun leg(id: String, amount: Int, category: String) =
        expense(id = id, amount = amount).copy(
            splitId = receipt,
            categoryId = categoryId(category),
        )

    "a receipt's legs render as one row carrying the whole payment" {
        runTest {
            val env = Env(
                transactions = listOf(
                    leg(id = "leg-groceries", amount = 30, category = "c-food"),
                    leg(id = "leg-chemicals", amount = 4, category = "c-home"),
                    expense(id = "coffee", amount = 3),
                ),
            )

            val rows = env.viewModel().content().rows()

            rows shouldHaveSize 2
            val collapsed = rows.first { it.id.value == "leg-groceries" }
            collapsed.formattedAmount shouldBe "$34.00"
            collapsed.splitCategoryCount shouldBe 2
            // The ordinary row beside it keeps its category subtitle and no badge.
            rows.first { it.id.value == "coffee" }.splitCategoryCount shouldBe 0
        }
    }

    "the day's net counts every leg, not just the one standing for the group" {
        runTest {
            val env = Env(
                transactions = listOf(
                    leg(id = "leg-groceries", amount = 30, category = "c-food"),
                    leg(id = "leg-chemicals", amount = 4, category = "c-home"),
                ),
            )

            env.viewModel().content().groups.single().netFormatted shouldBe "-$34.00"
        }
    }

    "a group the page cut in half renders as its legs rather than a short total" {
        // Only the first leg is in the window; collapsing it would show $30.00 for a $34.00
        // receipt, and that figure would change on its own once the next page arrived.
        val legs = listOf(
            CategorizedTransaction(
                transaction = leg(id = "leg-groceries", amount = 30, category = "c-food"),
                categoryName = "Groceries",
                splitLegCount = 2,
            ),
        )

        val receipts = collapseSplitLegs(legs)

        receipts shouldHaveSize 1
        receipts.single().isSplit shouldBe false
    }

    "a filter that matches one leg shows that leg, not the receipt" {
        runTest {
            val env = Env(
                transactions = listOf(
                    leg(id = "leg-groceries", amount = 30, category = "c-food"),
                    leg(id = "leg-chemicals", amount = 4, category = "c-home"),
                ),
            )
            val viewModel = env.viewModel()

            // Amount filters run in memory over the loaded page, so this is the general "the page
            // holds part of a group" case: what is left must describe itself honestly.
            env.filterStore.commit(env.filterStore.filters.value.copy(minAmount = "10"))

            val rows = viewModel.content().rows()
            rows shouldHaveSize 1
            rows.single().id.value shouldBe "leg-groceries"
            rows.single().formattedAmount shouldBe "$30.00"
            rows.single().splitCategoryCount shouldBe 0
        }
    }

    "oldest-first sorting keeps a receipt as one row" {
        runTest {
            val env = Env(
                transactions = listOf(
                    leg(id = "leg-groceries", amount = 30, category = "c-food"),
                    leg(id = "leg-chemicals", amount = 4, category = "c-home"),
                    expense(id = "coffee", amount = 3),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(env.filterStore.filters.value.copy(sort = TransactionSort.Oldest))

            viewModel.content().rows() shouldHaveSize 2
        }
    }
})
