package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * The split editor's state transitions on their own, without a ViewModel around them.
 *
 * Each is a `Content -> Content` function of its arguments, so the arithmetic that decides what a
 * line holds — and the rule that the legs always add up to the amount above them — is worth pinning
 * directly rather than only through the events that happen to reach it.
 */
class TransactionCreationSplitEditingTest : StringSpec({

    val ws = workspaceId("ws-1")
    val groceries = aCategory(id = categoryId("c-food"), workspaceId = ws, type = CategoryType.EXPENSE)
    val household = aCategory(id = categoryId("c-home"), workspaceId = ws, type = CategoryType.EXPENSE)
    val salary = aCategory(id = categoryId("c-salary"), workspaceId = ws, type = CategoryType.INCOME)

    fun content(
        amount: String = "34",
        selected: Category? = groceries,
        lines: List<TransactionSplitLineUi> = emptyList(),
    ) = TransactionCreationState.Content(
        amount = amount,
        note = "",
        type = TransactionTypeUi.Expense,
        accounts = emptyList(),
        categories = listOf(groceries, household, salary),
        selectedAccount = null,
        selectedCategory = selected,
        isEditMode = false,
        editingTransactionId = null,
        timestamp = 0L,
        categoryUsageCounts = emptyMap(),
        displayCategories = emptyList(),
        splitLines = lines,
    )

    "toggling on seeds the minimum a split is, carrying the form's category into the first line" {
        var next = 0
        val toggled = content().withSplitToggled { next++ }

        toggled.splitLines shouldHaveSize MIN_SPLIT_LINES
        toggled.splitLines.first().category shouldBe groceries
        toggled.splitLines.last().category shouldBe null
        // Distinct keys: two lines picking the same category must stay two editable rows.
        toggled.splitLines.map { it.key } shouldBe listOf(0, 1)
    }

    "toggling off discards the lines and leaves the rest of the form alone" {
        val open = content().withSplitToggled { 0 }

        val closed = open.withSplitToggled { 99 }

        closed.isSplit shouldBe false
        closed.splitLines.shouldBeEmpty()
        closed.amount shouldBe "34"
        closed.selectedCategory shouldBe groceries
    }

    "the trailing line holds the remainder and the ones above it hold what was typed" {
        val state = content(
            amount = "34",
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "30"),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        state.splitTotal shouldBe 34.dollars
        state.splitRemainder shouldBe 4.dollars
        state.splitAmounts shouldBe listOf(30.dollars, 4.dollars)
        state.isSplitComplete shouldBe true
    }

    "an unparseable amount counts as nothing rather than breaking the arithmetic" {
        val state = content(
            amount = "34",
            lines = listOf(
                // Rejected by TransactionAmountInput: two separators is not a plain decimal.
                TransactionSplitLineUi(key = 0, category = groceries, amount = "1.2.3"),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        state.splitAmounts shouldBe listOf(Money.zero(), 34.dollars)
        // A zero leg is not a leg, so the form cannot be saved in this state.
        state.isSplitComplete shouldBe false
    }

    "a half-typed amount is taken at face value while the user keeps typing" {
        val state = content(
            amount = "34",
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "3."),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        // "3." parses to 3.00, so the remainder follows every keystroke rather than jumping to the
        // whole amount and back the moment a decimal point is typed.
        state.splitAmounts shouldBe listOf(3.dollars, 31.dollars)
    }

    "over-assigning drives the remainder negative, which is not a saveable receipt" {
        val state = content(
            amount = "10",
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "12"),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        state.splitRemainder shouldBe Money.fromMinor(-200)
        state.isSplitComplete shouldBe false
    }

    "a line with no category is not a saveable receipt either" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "30"),
                TransactionSplitLineUi(key = 1),
            ),
        )

        state.isSplitComplete shouldBe false
    }

    "removing a line below the minimum closes the editor rather than leaving one leg" {
        val state = content().withSplitToggled { 0 }

        state.withSplitLineRemoved(key = state.splitLines.last().key).isSplit shouldBe false
    }

    "removing one of three keeps the other two" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "20"),
                TransactionSplitLineUi(key = 1, category = household, amount = "5"),
                TransactionSplitLineUi(key = 2, category = groceries),
            ),
        )

        val remaining = state.withSplitLineRemoved(key = 1).splitLines

        remaining.map { it.key } shouldBe listOf(0, 2)
    }

    "typing into a line touches only that line" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        val typed = state.withSplitLineAmount(key = 0, amount = "12.50")

        typed.splitLines.first().amount shouldBe "12.50"
        typed.splitLines.last().amount shouldBe ""
        typed.splitAmounts shouldBe listOf(Money.fromMinor(1_250), Money.fromMinor(2_150))
    }

    "the legs a save would write pair each line's category with its share" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries, amount = "30"),
                TransactionSplitLineUi(key = 1, category = household),
            ),
        )

        state.splitLegs().map { it.categoryId } shouldBe listOf(groceries.id, household.id)
        state.splitLegs().map { it.money } shouldBe listOf(30.dollars, 4.dollars)
    }

    "a picked category lands in the slot the chooser was opened for" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = groceries),
                TransactionSplitLineUi(key = 1),
            ),
        )

        val onLine = state.withPickedCategory(
            categories = state.categories,
            selected = household,
            slot = CategorySlot.SplitLine(key = 1),
        )
        onLine.splitLines.last().category shouldBe household
        // The form's own category is not the line's, and must not follow it.
        onLine.selectedCategory shouldBe groceries

        val onForm = state.withPickedCategory(
            categories = state.categories,
            selected = household,
            slot = CategorySlot.Single,
        )
        onForm.selectedCategory shouldBe household
        onForm.splitLines.map { it.category } shouldBe listOf(groceries, null)
    }

    // The fallback when a just-created category is not in the loaded list yet: the slot's current
    // value is what the refresh falls back to, so a failed lookup leaves the form as it was.
    "a slot reports what it currently holds" {
        val state = content(
            lines = listOf(
                TransactionSplitLineUi(key = 0, category = household),
                TransactionSplitLineUi(key = 1),
            ),
        )

        state.categoryInSlot(CategorySlot.Single) shouldBe groceries
        state.categoryInSlot(CategorySlot.SplitLine(key = 0)) shouldBe household
        state.categoryInSlot(CategorySlot.SplitLine(key = 1)) shouldBe null
        // A line that no longer exists resolves to nothing rather than throwing.
        state.categoryInSlot(CategorySlot.SplitLine(key = 404)) shouldBe null
    }

    "a stored leg is flagged in edit mode so the delete confirmation can name the receipt" {
        val editing = content(lines = emptyList()).copy(
            isEditMode = true,
            preserved = PreservedTransactionFields(splitId = SplitId("sp-1")),
        )

        editing.isEditingSplitLeg shouldBe true
        // The editor itself stays closed in edit mode — the two are different questions.
        editing.isSplit shouldBe false
        editing.copy(preserved = PreservedTransactionFields()).isEditingSplitLeg shouldBe false
        content().copy(preserved = PreservedTransactionFields(splitId = SplitId("sp-1")))
            .isEditingSplitLeg shouldBe false
    }
})
