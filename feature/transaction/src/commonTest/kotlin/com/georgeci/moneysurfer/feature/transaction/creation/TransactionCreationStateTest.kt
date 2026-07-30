package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val WS = workspaceId("ws-1")
private val CHECKING = anAccount(id = accountId("a-1"), workspaceId = WS, currencyCode = USD)
private val SAVINGS = anAccount(id = accountId("a-2"), workspaceId = WS, name = "Savings", currencyCode = USD)
private val ABROAD = anAccount(id = accountId("a-3"), workspaceId = WS, name = "Abroad", currencyCode = EUR)
private val GROCERIES = aCategory(id = categoryId("c-exp"), workspaceId = WS)

/**
 * What the Save button reads, per form shape.
 *
 * The three branches — transfer, split, plain — gate on different things, and each of them is the
 * last check before a write: the ViewModel's own guards refuse the same forms, but silently, so a
 * button that enabled itself here would look like a save that does nothing.
 */
class TransactionCreationStateTest : StringSpec({

    "a plain transaction needs an amount, an account and a category" {
        val complete = aCreationContent(
            amount = "80",
            accounts = listOf(CHECKING),
            categories = listOf(GROCERIES),
        )

        complete.isSaveEnabled shouldBe true
        complete.copy(amount = "").isSaveEnabled shouldBe false
        complete.copy(amount = "0").isSaveEnabled shouldBe false
        complete.copy(selectedAccount = null).isSaveEnabled shouldBe false
        complete.copy(selectedCategory = null).isSaveEnabled shouldBe false
    }

    "an amount is flagged inline only once there is something to complain about" {
        val form = aCreationContent(accounts = listOf(CHECKING), categories = listOf(GROCERIES))

        form.amountError.shouldBeNull()
        form.copy(amount = "1.2.3").amountError shouldBe TransactionAmountError.INVALID_FORMAT
        form.copy(amount = "0").amountError shouldBe TransactionAmountError.NOT_POSITIVE
    }

    "a transfer needs two different accounts and drops the category requirement" {
        val transfer = aCreationContent(
            amount = "80",
            type = TransactionTypeUi.Transfer,
            accounts = listOf(CHECKING, SAVINGS),
            selectedCategory = null,
            fromAccount = CHECKING,
            toAccount = SAVINGS,
        )

        transfer.isSaveEnabled shouldBe true
        transfer.copy(toAccount = null).isSaveEnabled shouldBe false
        transfer.copy(fromAccount = null).isSaveEnabled shouldBe false
        // Money cannot move from an account into itself.
        transfer.copy(toAccount = CHECKING).isSaveEnabled shouldBe false
    }

    "only a cross-currency transfer asks for the receiving amount" {
        val sameCurrency = aCreationContent(
            amount = "80",
            type = TransactionTypeUi.Transfer,
            accounts = listOf(CHECKING, SAVINGS),
            selectedCategory = null,
            fromAccount = CHECKING,
            toAccount = SAVINGS,
        )
        val crossCurrency = sameCurrency.copy(toAccount = ABROAD)

        sameCurrency.crossCurrency shouldBe false
        sameCurrency.toAmountError.shouldBeNull()
        // The second field is not even rendered there, so an empty one cannot block the save.
        sameCurrency.copy(toAmount = "").isSaveEnabled shouldBe true

        crossCurrency.crossCurrency shouldBe true
        crossCurrency.isSaveEnabled shouldBe false
        crossCurrency.copy(toAmount = "74").isSaveEnabled shouldBe true
        crossCurrency.copy(toAmount = "abc").toAmountError shouldBe TransactionAmountError.INVALID_FORMAT
    }

    "the receipt's last line holds whatever the ones above it left" {
        val split = aCreationContent(
            amount = "100",
            accounts = listOf(CHECKING),
            categories = listOf(GROCERIES),
            splitLines = listOf(
                TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "30"),
                TransactionSplitLineUi(key = 1, category = GROCERIES, amount = "20"),
                TransactionSplitLineUi(key = 2, category = GROCERIES),
            ),
        )

        split.splitTotal shouldBe 100.dollars
        split.splitRemainder shouldBe 50.dollars
        split.splitAmounts shouldBe listOf(30.dollars, 20.dollars, 50.dollars)
        split.isSplitComplete shouldBe true
        split.isSaveEnabled shouldBe true
    }

    "an amount the field cannot parse counts as nothing rather than breaking the arithmetic" {
        val split = aCreationContent(
            amount = "1.2.3",
            accounts = listOf(CHECKING),
            categories = listOf(GROCERIES),
            splitLines = listOf(
                TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "abc"),
                TransactionSplitLineUi(key = 1, category = GROCERIES),
            ),
        )

        split.splitTotal shouldBe Money.zero()
        split.splitRemainder shouldBe Money.zero()
        // Zero is not a leg anyone can save, but it is arithmetic the editor can keep rendering.
        split.isSplitComplete shouldBe false
        split.isSaveEnabled shouldBe false
    }

    "a receipt is not saveable until every line has a category and a positive share" {
        val split = aCreationContent(
            amount = "100",
            accounts = listOf(CHECKING),
            categories = listOf(GROCERIES),
            splitLines = listOf(
                TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "30"),
                TransactionSplitLineUi(key = 1, category = GROCERIES),
            ),
        )

        split.isSaveEnabled shouldBe true
        split.copy(
            splitLines = split.splitLines.map { it.copy(category = null) },
        ).isSaveEnabled shouldBe false
        // Over-assigned: the trailing line's remainder goes negative.
        split.copy(
            splitLines = listOf(
                TransactionSplitLineUi(key = 0, category = GROCERIES, amount = "140"),
                TransactionSplitLineUi(key = 1, category = GROCERIES),
            ),
        ).isSaveEnabled shouldBe false
        // A single line is an ordinary transaction, not a receipt.
        split.copy(
            splitLines = listOf(TransactionSplitLineUi(key = 0, category = GROCERIES)),
        ).isSplitComplete shouldBe false
        // …and a split still needs the account it is charged to.
        split.copy(selectedAccount = null).isSaveEnabled shouldBe false
    }

    "an empty editor is not a split at all" {
        val plain = aCreationContent(
            amount = "80",
            accounts = listOf(CHECKING),
            categories = listOf(GROCERIES),
        )

        plain.isSplit shouldBe false
        plain.splitAmounts shouldBe emptyList()
        plain.isEditingSplitLeg shouldBe false
    }
})
