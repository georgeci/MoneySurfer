package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.feature.transaction.details.SplitBreakdown
import com.georgeci.moneysurfer.feature.transaction.details.SplitLegUi
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsContent
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsEvent
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsState
import com.georgeci.moneysurfer.feature.transaction.details.TransactionDetailsTestTags
import com.georgeci.moneysurfer.feature.transaction.details.TransferLeg
import com.georgeci.moneysurfer.uikit.components.transaction.SurferDeleteTransactionDialogTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

/**
 * Desktop UI cover for the transaction details screen — see docs/testing/testing-strategy.md.
 *
 * The screen picks its own words from the state: which rows the card carries, what the hero band
 * says above the amount, and whether the Duplicate button is there at all. A ViewModel test can see
 * `canDuplicate`; only this can see that the button follows it.
 */
@OptIn(ExperimentalTestApi::class)
class TransactionDetailsScreenStateTest : StringSpec({

    "an expense names its account, merchant, category and reference" {
        runComposeUiTest {
            setContent { TransactionDetailsContent(state = expense(), onEvent = {}) }

            onNodeWithTag(TransactionDetailsTestTags.Root).assertIsDisplayed()
            onNodeWithText(EXPENSE_AMOUNT).assertIsDisplayed()
            onNodeWithText("GROCERIES · EXPENSE · POSTED").assertIsDisplayed()
            onNodeWithText("Account").assertIsDisplayed()
            onNodeWithText("Everyday").assertIsDisplayed()
            onNodeWithText("Merchant").assertIsDisplayed()
            onNodeWithText("Lidl").assertIsDisplayed()
            // Nested categories read leaf-first, so the row says where the leaf sits.
            onNodeWithText("Groceries · in Food").assertIsDisplayed()
            onNodeWithText("TX-8A13").assertIsDisplayed()
            onNodeWithText(DATE).assertIsDisplayed()
        }
    }

    "income turns the two counterparty rows around" {
        runComposeUiTest {
            setContent {
                TransactionDetailsContent(
                    state = expense().copy(
                        type = TransactionType.INCOME,
                        formattedAmount = "+€1,200.00",
                        merchant = "Acme",
                        categoryName = "Salary",
                        parentCategoryName = null,
                    ),
                    onEvent = {},
                )
            }

            // Money lands *in* the account and comes *from* the payer, which is the opposite of
            // what the same two rows mean on an expense.
            onNodeWithText("To account").assertIsDisplayed()
            onNodeWithText("From").assertIsDisplayed()
            onNodeWithText("SALARY · INCOME · POSTED").assertIsDisplayed()
        }
    }

    "a transfer names both accounts instead of an account and a category" {
        runComposeUiTest {
            setContent {
                TransactionDetailsContent(
                    state = expense().copy(
                        transfer = TransferLeg(fromAccountName = "Everyday", toAccountName = "Savings"),
                        formattedAmount = "€120.00",
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("From").assertIsDisplayed()
            onNodeWithText("To").assertIsDisplayed()
            onNodeWithText("Savings").assertIsDisplayed()
            // Its category is the seeded system one, so naming it would only repeat the type.
            onNodeWithText("TRANSFER · POSTED").assertIsDisplayed()
            onNodeWithText("Category").assertDoesNotExist()
        }
    }

    "a planned transaction says so in the hero band" {
        runComposeUiTest {
            setContent { TransactionDetailsContent(state = expense().copy(isPlanned = true), onEvent = {}) }

            onNodeWithText("GROCERIES · EXPENSE · PLANNED").assertIsDisplayed()
        }
    }

    "a split leg breaks the receipt down and marks the leg being read" {
        runComposeUiTest {
            setContent {
                TransactionDetailsContent(
                    state = expense().copy(
                        split = SplitBreakdown(
                            formattedTotal = "€82.40",
                            legs = listOf(
                                SplitLegUi(
                                    transactionId = TransactionId("t-1"),
                                    categoryName = "Groceries",
                                    formattedAmount = "€42.10",
                                    isCurrent = true,
                                ),
                                SplitLegUi(
                                    transactionId = TransactionId("t-2"),
                                    categoryName = "",
                                    formattedAmount = "€40.30",
                                    isCurrent = false,
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Split across 2 categories").assertIsDisplayed()
            onNodeWithText("Receipt total").assertIsDisplayed()
            onNodeWithText("€82.40").assertIsDisplayed()
            // A leg with no category still needs a name on the row.
            onNodeWithText("Uncategorized").assertIsDisplayed()
        }
    }

    "the three toolbar actions each ask for their own destination" {
        runComposeUiTest {
            val events = mutableListOf<TransactionDetailsEvent>()
            setContent { TransactionDetailsContent(state = expense(), onEvent = { events += it }) }

            onNodeWithTag(TransactionDetailsTestTags.Edit).performClick()
            onNodeWithTag(TransactionDetailsTestTags.Delete).performClick()
            onNodeWithText("Duplicate").performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                TransactionDetailsEvent.OnEditClick,
                TransactionDetailsEvent.OnDeleteClick,
                TransactionDetailsEvent.OnDuplicateClick,
            )
        }
    }

    "neither leg of a pair offers Duplicate" {
        runComposeUiTest {
            setContent {
                TransactionDetailsContent(
                    state = expense().copy(
                        transfer = TransferLeg(fromAccountName = "Everyday", toAccountName = "Savings"),
                    ),
                    onEvent = {},
                )
            }

            // Duplicating one leg would write half a transfer.
            onNodeWithText("Duplicate").assertDoesNotExist()
        }
    }

    "deleting goes through a confirmation that names the transaction" {
        runComposeUiTest {
            val events = mutableListOf<TransactionDetailsEvent>()
            setContent {
                TransactionDetailsContent(
                    state = expense().copy(showDeleteConfirmation = true),
                    onEvent = { events += it },
                )
            }

            // The dialog names the row it is about to take, so the answer is about that row —
            // the hero above it carries the same note, hence two nodes rather than one.
            onAllNodesWithText(NOTE, substring = true).assertCountEquals(2)
            onNodeWithTag(SurferDeleteTransactionDialogTestTags.Confirm).performClick()
            waitForIdle()

            events shouldContainExactly listOf(TransactionDetailsEvent.OnDeleteConfirmed)
        }
    }
})

private const val EXPENSE_AMOUNT = "−€42.10"
private const val DATE = "27 Mar 2025"
private const val NOTE = "Weekly shop"

private fun expense() = TransactionDetailsState.Content(
    transactionId = TransactionId("t-1"),
    formattedAmount = EXPENSE_AMOUNT,
    type = TransactionType.EXPENSE,
    note = NOTE,
    merchant = "Lidl",
    accountName = "Everyday",
    categoryName = "Groceries",
    parentCategoryName = "Food",
    categoryId = "c-1",
    categoryIconKey = "cart",
    categoryHue = 35,
    tags = emptyList(),
    reference = "TX-8A13",
    formattedDate = DATE,
    isPlanned = false,
    showDeleteConfirmation = false,
)
