package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.transferId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val WS = workspaceId("ws-1")

/**
 * The pure mapping the creation screen is built from: how a stored row becomes a filled form, how
 * money becomes the text of the amount field, and which categories the grid offers. Each of these
 * is a function of its arguments alone, so it is tested without a ViewModel around it.
 */
class TransactionCreationMappingTest : StringSpec({

    "the amount field spells a whole number without its trailing zeros" {
        80.dollars.toAmountInput() shouldBe "80"
        Money.fromMinor(1250).toAmountInput() shouldBe "12.5"
        Money.fromMinor(1205).toAmountInput() shouldBe "12.05"
        Money.zero().toAmountInput() shouldBe "0"
    }

    "every type but income files under the expense side" {
        TransactionTypeUi.Income.categoryType() shouldBe CategoryType.INCOME
        TransactionTypeUi.Expense.categoryType() shouldBe CategoryType.EXPENSE
        // A transfer has no category of its own; the picker still has to offer one side.
        TransactionTypeUi.Transfer.categoryType() shouldBe CategoryType.EXPENSE
    }

    "a chosen account lands in the slot the chooser was opened for" {
        val single = anAccount(id = accountId("a-1"), workspaceId = WS)
        val other = anAccount(id = accountId("a-2"), workspaceId = WS, name = "Savings")
        val base = aCreationContent(accounts = listOf(single, other), selectedAccount = single)

        base.withAccountInSlot(other, AccountSlot.Single).selectedAccount?.id shouldBe other.id
        base.withAccountInSlot(other, AccountSlot.From).let {
            it.fromAccount?.id shouldBe other.id
            it.selectedAccount?.id shouldBe single.id
        }
        base.withAccountInSlot(other, AccountSlot.To).toAccount?.id shouldBe other.id
    }

    "the default category is the most used one of the type, or none at all" {
        val food = aCategory(id = categoryId("c-food"), workspaceId = WS, type = CategoryType.EXPENSE)
        val transport = aCategory(
            id = categoryId("c-transport"),
            workspaceId = WS,
            type = CategoryType.EXPENSE,
            name = "Transport",
        )
        val counts = mapOf(food.id to 2, transport.id to 9)

        pickDefaultCategory(listOf(food, transport), counts, CategoryType.EXPENSE)?.id shouldBe transport.id
        // Nothing on the income side yet — the form opens with no category rather than an expense one.
        pickDefaultCategory(listOf(food, transport), counts, CategoryType.INCOME).shouldBeNull()
    }

    "the grid shows the seven most used categories of the type" {
        val categories = List(9) { index ->
            aCategory(id = categoryId("c-$index"), workspaceId = WS, name = "Category $index")
        }
        val counts = categories.mapIndexed { index, category -> category.id to index }.toMap()

        val shown = buildDisplayCategories(categories, counts, CategoryType.EXPENSE, selected = null)

        shown.map { it.id } shouldBe categories.reversed().take(7).map { it.id }
    }

    "a selected category outside the top seven takes the last slot rather than disappearing" {
        val categories = List(9) { index ->
            aCategory(id = categoryId("c-$index"), workspaceId = WS, name = "Category $index")
        }
        val counts = categories.mapIndexed { index, category -> category.id to index }.toMap()
        val rarest = categories.first()

        val shown = buildDisplayCategories(categories, counts, CategoryType.EXPENSE, selected = rarest)

        shown.size shouldBe 7
        shown.last().id shouldBe rarest.id
    }

    "a selection belonging to the other side is not carried into the grid" {
        val expense = aCategory(id = categoryId("c-exp"), workspaceId = WS, type = CategoryType.EXPENSE)
        val income = aCategory(id = categoryId("c-inc"), workspaceId = WS, type = CategoryType.INCOME)

        val shown = buildDisplayCategories(
            categories = listOf(expense, income),
            counts = emptyMap(),
            type = CategoryType.EXPENSE,
            selected = income,
        )

        shown.map { it.id } shouldBe listOf(expense.id)
    }

    "duplicating an income row fills the form without any of the original's identity" {
        val account = anAccount(id = accountId("a-1"), workspaceId = WS)
        val salary = aCategory(
            id = categoryId("c-inc"),
            workspaceId = WS,
            type = CategoryType.INCOME,
            name = "Salary",
        )
        val original = aTransaction(
            id = transactionId("t-1"),
            workspaceId = WS,
            accountId = account.id,
            money = 1_200.dollars,
            categoryId = salary.id,
            note = "March",
            merchant = "Acme",
            type = TransactionType.INCOME,
        )

        val filled = aCreationContent(accounts = listOf(account), categories = listOf(salary)).seededFrom(
            seed = TransactionCreationSeed(original.id, TransactionCreationSeed.Mode.Duplicate),
            transaction = original,
        )

        filled.type shouldBe TransactionTypeUi.Income
        filled.amount shouldBe "1200"
        filled.note shouldBe "March"
        filled.selectedCategory?.id shouldBe salary.id
        filled.isEditMode shouldBe false
        filled.editingTransactionId.shouldBeNull()
        filled.editIdentity.shouldBeNull()
        // Merchant and tags travel; the pairing and the timestamps do not.
        filled.preserved shouldBe PreservedTransactionFields(merchant = "Acme")
        filled.editingCreatedAt.shouldBeNull()
        filled.pinnedOperationDate.shouldBeNull()
    }

    "a row whose account or category is not loaded keeps the form's current selection" {
        val loadedAccount = anAccount(id = accountId("a-1"), workspaceId = WS)
        val loadedCategory = aCategory(id = categoryId("c-exp"), workspaceId = WS)
        val elsewhere = aTransaction(
            id = transactionId("t-1"),
            workspaceId = WS,
            accountId = accountId("a-archived"),
            categoryId = categoryId("c-deleted"),
        )

        val filled = aCreationContent(
            accounts = listOf(loadedAccount),
            categories = listOf(loadedCategory),
            selectedAccount = loadedAccount,
            selectedCategory = loadedCategory,
        ).seededFrom(
            seed = TransactionCreationSeed(elsewhere.id, TransactionCreationSeed.Mode.Edit),
            transaction = elsewhere,
        )

        filled.selectedAccount?.id shouldBe loadedAccount.id
        filled.selectedCategory?.id shouldBe loadedCategory.id
        filled.isEditMode shouldBe true
        filled.editingTransactionId shouldBe elsewhere.id
    }

    "the identity band signs an expense and an income, and leaves a transfer leg unsigned" {
        val category = aCategory(id = categoryId("c-exp"), workspaceId = WS)
        val expense = aTransaction(
            id = transactionId("t-1"),
            workspaceId = WS,
            money = 80.dollars,
            currencyCode = USD,
            categoryId = category.id,
            note = "Lidl",
        )

        identityOf(expense, category).formattedAmount shouldBe "−$80.00"
        identityOf(expense.copy(type = TransactionType.INCOME), category).formattedAmount shouldBe "+$80.00"
        // Money that only moved sideways neither arrived nor left, so it carries no sign.
        identityOf(expense.copy(transferId = transferId("tr-1")), category).let {
            it.formattedAmount shouldBe "$80.00"
            it.type shouldBe TransactionTypeUi.Transfer
        }
        identityOf(expense.copy(type = TransactionType.OPENING_BALANCE), category)
            .formattedAmount shouldBe "$80.00"
    }

    "the identity band falls back to the merchant, and survives a category it cannot resolve" {
        val transaction = aTransaction(
            id = transactionId("t-1"),
            workspaceId = WS,
            money = 80.dollars,
            note = "",
            merchant = "Lidl",
        )

        val identity = identityOf(transaction, category = null)

        identity.note shouldBe "Lidl"
        identity.reference shouldBe transaction.id.reference
        identity.categoryId shouldBe ""
        identity.categoryIconKey shouldBe ""
        identity.categoryHue shouldBe CategoryAppearance.UNSET_HUE
        identity.categorySystemKind.shouldBeNull()
    }
})
