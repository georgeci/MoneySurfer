package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Delete + Undo against real Room. Verifies that the entity captured at delete time can be
 * re-applied to fully restore the row (and, for transactions, the account balance) — the
 * persistence guarantee the in-app Undo Snackbar relies on.
 */
class DeleteUndoIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
    }

    afterEach { harness.close() }

    "deleting a transaction then re-applying it restores the row and the balance" {
        val applyChange = ApplyTransactionChangeUseCase(stack.transactionRepository, stack.accountRepository)
        val deleteTransaction = DeleteTransactionUseCase(stack.transactionRepository, applyChange)

        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 500.dollars,
        )
        stack.accountRepository.insert(account)
        stack.categoryRepository.insert(aCategory(id = categoryId("c-1"), workspaceId = DEFAULT_WORKSPACE_ID))
        stack.createTransaction(
            aTransaction(
                id = transactionId("t-1"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account.id,
                money = 80.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                type = TransactionType.EXPENSE,
            ),
        )
        stack.accountRepository.getById(account.id)!!.balance shouldBe 420.dollars

        val deleted = deleteTransaction(transactionId("t-1"))
        deleted.shouldNotBeNull()
        stack.transactionRepository.getById(transactionId("t-1")) shouldBe null
        stack.accountRepository.getById(account.id)!!.balance shouldBe 500.dollars

        applyChange(old = null, new = deleted)

        stack.transactionRepository.getById(transactionId("t-1")).shouldNotBeNull()
        stack.accountRepository.getById(account.id)!!.balance shouldBe 420.dollars
    }

    "deleting a category then re-inserting it restores the row" {
        val deleteCategory = DeleteCategoryUseCase(stack.categoryRepository)
        val category = aCategory(id = categoryId("c-1"), workspaceId = DEFAULT_WORKSPACE_ID, name = "Food")
        stack.categoryRepository.insert(category)

        val deleted = deleteCategory(categoryId("c-1"))
        deleted.shouldNotBeNull()
        deleted.category.name shouldBe "Food"
        stack.categoryRepository.getById(categoryId("c-1")) shouldBe null

        stack.categoryRepository.insert(deleted.category)

        stack.categoryRepository.getById(categoryId("c-1")).shouldNotBeNull()
    }

    // Against the real Room stack, not a fake: the parentId foreign key has no cascade, so a
    // delete that did not move the children out of the way first would be rejected outright.
    "deleting a parent category moves its children to the root, and undo re-attaches them" {
        val deleteCategory = DeleteCategoryUseCase(stack.categoryRepository)
        val parent = aCategory(id = categoryId("c-parent"), workspaceId = DEFAULT_WORKSPACE_ID, name = "Food")
        val child = aCategory(
            id = categoryId("c-child"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            name = "Groceries",
            parentId = parent.id,
        )
        stack.categoryRepository.insert(parent)
        stack.categoryRepository.insert(child)

        val deleted = deleteCategory(parent.id)

        deleted.shouldNotBeNull()
        deleted.reparentedChildren.map { it.id } shouldBe listOf(child.id)
        stack.categoryRepository.getById(parent.id) shouldBe null
        stack.categoryRepository.getById(child.id)!!.parentId shouldBe null

        stack.categoryRepository.insert(deleted.category)
        deleted.reparentedChildren.forEach { stack.categoryRepository.update(it) }

        stack.categoryRepository.getById(child.id)!!.parentId shouldBe parent.id
    }

    "a category's icon and colour survive a round-trip through Room" {
        val category = aCategory(id = categoryId("c-look"), workspaceId = DEFAULT_WORKSPACE_ID)
            .copy(iconKey = CategoryAppearance.ICON_KEYS.last(), hue = CategoryAppearance.HUES.last())
        stack.categoryRepository.insert(category)

        val loaded = stack.categoryRepository.getById(category.id).shouldNotBeNull()

        loaded.iconKey shouldBe CategoryAppearance.ICON_KEYS.last()
        loaded.hue shouldBe CategoryAppearance.HUES.last()
    }
})
