package com.georgeci.moneysurfer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationContent
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationEvent
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationState
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationTestTags
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionEditIdentity
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionSplitLineUi
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionSplitTestTags
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionTypeUi
import com.georgeci.moneysurfer.uikit.components.transaction.SurferDeleteTransactionDialogTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import java.util.Locale
import kotlin.time.Instant

/**
 * Desktop UI cover for the transaction creation / edit form — see docs/testing/testing-strategy.md.
 *
 * The form decides for itself which blocks to draw: a transfer replaces the category grid with two
 * account slots, the split editor replaces the grid with its lines, and edit mode drops the Transfer
 * segment while adding the identity band and the delete action. None of that is in the state as a
 * flag — it is read off the state by the screen, which is what these cases pin down.
 */
@OptIn(ExperimentalTestApi::class)
class TransactionCreationScreenStateTest : StringSpec({

    // The split editor's running total is formatted by `MoneyFormatter`, which reads
    // `Locale.getDefault()` on the JVM — a machine defaulting to ru_RU renders USD as "70,00 $"
    // and the assertions below would fail on a build with nothing wrong with it. Same pin the
    // domain's own `MoneyFormatterTest` uses.
    lateinit var originalLocale: Locale

    beforeSpec {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    afterSpec { Locale.setDefault(originalLocale) }

    "a new expense opens on the Expense segment with the category grid under it" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = form(), onEvent = {}) }

            onNodeWithTag(TransactionCreationTestTags.Root).assertIsDisplayed()
            onNodeWithTag(typeTag(TransactionTypeUi.Expense)).assertIsSelected()
            onNodeWithTag(typeTag(TransactionTypeUi.Income)).assertIsNotSelected()
            onNodeWithTag(TransactionCreationTestTags.Amount).assertIsDisplayed()
            onNodeWithText("Groceries").assertIsDisplayed()
            onNodeWithText("All categories").assertIsDisplayed()
            onNodeWithText("Everyday").assertIsDisplayed()
            onNodeWithText("Note").assertIsDisplayed()
            onNodeWithText("Today").assertIsDisplayed()
        }
    }

    "the type segments ask the view model for the switch rather than deciding on screen" {
        runComposeUiTest {
            val events = mutableListOf<TransactionCreationEvent>()
            setContent { TransactionCreationContent(state = form(), onEvent = { events += it }) }

            onNodeWithTag(typeTag(TransactionTypeUi.Income)).performClick()
            waitForIdle()

            events.typed() shouldContainExactly listOf(
                TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income),
            )
        }
    }

    "a build without transfers draws no Transfer segment" {
        runComposeUiTest {
            setContent {
                TransactionCreationContent(state = form(transferEnabled = false), onEvent = {})
            }

            onNodeWithTag(typeTag(TransactionTypeUi.Transfer)).assertDoesNotExist()
        }
    }

    "an unusable amount is flagged under the field and blocks Save" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = form(amount = "1.2.3"), onEvent = {}) }

            onNodeWithText("Enter a valid amount.").assertIsDisplayed()
            onNodeWithTag(TransactionCreationTestTags.Save).assertIsNotEnabled()
        }
    }

    "a zero amount says what is wrong with it rather than only refusing" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = form(amount = "0"), onEvent = {}) }

            onNodeWithText("Amount must be greater than zero.").assertIsDisplayed()
        }
    }

    "a state the view model pushes back redraws the form it is already showing" {
        runComposeUiTest {
            val state = mutableStateOf(form(amount = "80"))
            setContent { TransactionCreationContent(state = state.value, onEvent = {}) }

            onNodeWithText("Enter a valid amount.").assertDoesNotExist()
            onNodeWithTag(TransactionCreationTestTags.Save).assertIsEnabled()

            // Every case above mounts the form once. This is the other half of the contract: the
            // screen is a function of the state, so a state arriving after composition — a picked
            // category, a restored draft, an amount the ViewModel rewrote — has to reach the
            // screen without it being torn down and rebuilt.
            runOnIdle { state.value = form(amount = "1.2.3") }

            onNodeWithText("Enter a valid amount.").assertIsDisplayed()
            onNodeWithTag(TransactionCreationTestTags.Save).assertIsNotEnabled()
        }
    }

    "a transfer swaps the category grid for two account slots" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = transferForm(), onEvent = {}) }

            onNodeWithText("FROM").assertIsDisplayed()
            onNodeWithText("TO").assertIsDisplayed()
            onNodeWithText("Savings · $0.00").assertIsDisplayed()
            // A transfer has no category of its own, so the grid has nothing to offer.
            onNodeWithText("All categories").assertDoesNotExist()
            onNodeWithText("Split").assertDoesNotExist()
        }
    }

    "a cross-currency transfer asks for the receiving amount as well" {
        runComposeUiTest {
            setContent {
                TransactionCreationContent(
                    state = transferForm(toAccount = ABROAD, amount = "100", toAmount = "92"),
                    onEvent = {},
                )
            }

            // One field per leg: with no rate to derive it from, the receiving amount is entered.
            onNodeWithText("100").assertIsDisplayed()
            onNodeWithText("92").assertIsDisplayed()
        }
    }

    "the split editor replaces the grid with its lines and says what is left to assign" {
        runComposeUiTest {
            setContent {
                TransactionCreationContent(
                    state = form(
                        amount = "100",
                        splitLines = listOf(
                            TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "30"),
                            TransactionSplitLineUi(key = 1, category = null),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(TransactionSplitTestTags.AddLine).assertIsDisplayed()
            onNodeWithText("Pick a category").assertIsDisplayed()
            onNodeWithText("$70.00 left to assign").assertIsDisplayed()
            // Each line picks its own category now, so the single-category grid is gone.
            onNodeWithText("All categories").assertDoesNotExist()
        }
    }

    "an over-assigned receipt says so instead of reporting a negative remainder" {
        runComposeUiTest {
            setContent {
                TransactionCreationContent(
                    state = form(
                        amount = "100",
                        splitLines = listOf(
                            TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "140"),
                            TransactionSplitLineUi(key = 1, category = GROCERIES),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("$40.00 over the amount").assertIsDisplayed()
            onNodeWithTag(TransactionCreationTestTags.Save).assertIsNotEnabled()
        }
    }

    "turning the split editor on is one row's job" {
        runComposeUiTest {
            val events = mutableListOf<TransactionCreationEvent>()
            setContent { TransactionCreationContent(state = form(), onEvent = { events += it }) }

            onNodeWithTag(TransactionSplitTestTags.Toggle).performClick()
            waitForIdle()

            events.typed() shouldContainExactly listOf(TransactionCreationEvent.OnSplitToggled)
        }
    }

    "edit mode names the row being edited and drops the way into a transfer" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = editForm(), onEvent = {}) }

            onNodeWithText("Edit transaction").assertIsDisplayed()
            onNodeWithText("EXPENSE · TX-8A13").assertIsDisplayed()
            onNodeWithText("−$42.10").assertIsDisplayed()
            // Twice over: the band's frozen snapshot of the stored row, and the note field the user
            // is free to type over — the band is what keeps naming the row they opened.
            onAllNodesWithText("Weekly shop").assertCountEquals(2)
            // An existing single-leg row cannot morph into a paired transfer in place.
            onNodeWithTag(typeTag(TransactionTypeUi.Transfer)).assertDoesNotExist()
            onNodeWithTag(TransactionCreationTestTags.Delete).assertIsDisplayed()
        }
    }

    "there is nothing to delete while a transaction is still being entered" {
        runComposeUiTest {
            setContent { TransactionCreationContent(state = form(), onEvent = {}) }

            onNodeWithTag(TransactionCreationTestTags.Delete).assertDoesNotExist()
        }
    }

    "the top bar's two actions are Close and Save" {
        runComposeUiTest {
            val events = mutableListOf<TransactionCreationEvent>()
            setContent {
                TransactionCreationContent(state = form(amount = "80"), onEvent = { events += it })
            }

            onNodeWithTag(TransactionCreationTestTags.Save).performClick()
            onNodeWithTag(TransactionCreationTestTags.Close).performClick()
            waitForIdle()

            events.typed() shouldContainExactly listOf(
                TransactionCreationEvent.OnSaveClick,
                TransactionCreationEvent.OnBackClick,
            )
        }
    }

    "deleting from the edit screen goes through the same confirmation the lists use" {
        runComposeUiTest {
            val events = mutableListOf<TransactionCreationEvent>()
            setContent {
                TransactionCreationContent(
                    state = editForm().copy(showDeleteConfirmation = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(SurferDeleteTransactionDialogTestTags.Confirm).performClick()
            waitForIdle()

            events.typed() shouldContainExactly listOf(TransactionCreationEvent.OnDeleteConfirmed)
        }
    }
})

private val WORKSPACE = WorkspaceId("ws-1")
private val USD = CurrencyCode("USD")
private val EUR = CurrencyCode("EUR")

private fun account(id: String, name: String, currency: CurrencyCode = USD) = Account(
    id = AccountId(id),
    workspaceId = WORKSPACE,
    name = name,
    type = AccountType.CASH,
    currencyCode = currency,
    balance = Money.zero(),
)

private val EVERYDAY = account("a-1", "Everyday")
private val SAVINGS = account("a-2", "Savings")
private val ABROAD = account("a-3", "Abroad", EUR)

private val GROCERIES = Category(
    id = CategoryId("c-1"),
    workspaceId = WORKSPACE,
    name = "Groceries",
    type = CategoryType.EXPENSE,
    parentId = null,
    createdAt = Instant.fromEpochMilliseconds(0),
)

private fun form(
    amount: String = "",
    transferEnabled: Boolean = true,
    splitLines: List<TransactionSplitLineUi> = emptyList(),
) = TransactionCreationState.Content(
    amount = amount,
    note = "",
    type = TransactionTypeUi.Expense,
    accounts = listOf(EVERYDAY, SAVINGS),
    categories = listOf(GROCERIES),
    selectedAccount = EVERYDAY,
    selectedCategory = GROCERIES,
    isEditMode = false,
    editingTransactionId = null,
    timestamp = 0L,
    categoryUsageCounts = emptyMap(),
    displayCategories = listOf(GROCERIES),
    transferEnabled = transferEnabled,
    splitLines = splitLines,
)

private fun transferForm(
    toAccount: Account = SAVINGS,
    amount: String = "",
    toAmount: String = "",
) = form(amount = amount).copy(
    type = TransactionTypeUi.Transfer,
    fromAccount = EVERYDAY,
    toAccount = toAccount,
    toAmount = toAmount,
)

private fun editForm() = form(amount = "42.10").copy(
    note = "Weekly shop",
    isEditMode = true,
    editingTransactionId = TransactionId("t-1"),
    editIdentity = TransactionEditIdentity(
        reference = "TX-8A13",
        type = TransactionTypeUi.Expense,
        note = "Weekly shop",
        formattedAmount = "−$42.10",
        categoryId = "c-1",
        categoryIconKey = "cart",
        categoryHue = 35,
        categorySystemKind = null,
    ),
)

/**
 * The form's two amount fields report their buffer to the ViewModel as soon as they are composed —
 * that is how a state-driven amount and a typed one stay in step — so every mount opens with an
 * `OnAmountChanged` / `OnToAmountChanged` pair that says nothing about what was tapped.
 */
private fun List<TransactionCreationEvent>.typed(): List<TransactionCreationEvent> = filterNot {
    it is TransactionCreationEvent.OnAmountChanged || it is TransactionCreationEvent.OnToAmountChanged
}

private fun typeTag(type: TransactionTypeUi) =
    TransactionCreationTestTags.TypePrefix + type.name.lowercase()
