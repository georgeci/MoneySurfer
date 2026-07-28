package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.transferId
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Delete + Undo against real Room — the persistence guarantee the in-app Undo Snackbar relies on.
 *
 * For transactions the delete is a tombstone since issue #346, so what is verified is that the row
 * disappears from the reads, comes back through a restore rather than a re-insert, and moves the
 * account balance both ways. Categories still hard-delete and still restore from the copy the
 * delete handed back — that difference is deliberate, and the tests below are written to show it.
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

    // The Undo now needs nothing but the id: the row itself never left. This is what makes the
    // interaction survive a Snackbar being replaced, or the process dying between the two halves —
    // the case that motivated issue #346.
    "an Undo built from nothing but the deleted id restores the row and the balance" {
        val applyChange = ApplyTransactionChangeUseCase(stack.transactionRepository, stack.accountRepository)
        val deleteTransaction = DeleteTransactionUseCase(stack.transactionRepository, applyChange)

        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 500.dollars,
        )
        stack.accountRepository.insert(account)
        stack.createTransaction(
            aTransaction(
                id = transactionId("t-1"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account.id,
                money = 80.dollars,
                currencyCode = USD,
                categoryId = null,
                type = TransactionType.EXPENSE,
            ),
        )

        deleteTransaction(transactionId("t-1"))
        stack.transactionRepository.getById(transactionId("t-1")) shouldBe null

        // Deliberately not the Transaction the delete returned — only its id.
        val restored = applyChange.restore(transactionId("t-1")).shouldNotBeNull()

        restored.money shouldBe 80.dollars
        stack.transactionRepository.getById(transactionId("t-1")).shouldNotBeNull()
        stack.accountRepository.getById(account.id)!!.balance shouldBe 420.dollars
    }

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
        deleted.map { it.id } shouldBe listOf(transactionId("t-1"))
        stack.transactionRepository.getById(transactionId("t-1")) shouldBe null
        stack.accountRepository.getById(account.id)!!.balance shouldBe 500.dollars

        RestoreTransactionsUseCase(applyChange)(deleted)

        stack.transactionRepository.getById(transactionId("t-1")).shouldNotBeNull()
        stack.accountRepository.getById(account.id)!!.balance shouldBe 420.dollars
    }

    // Both legs, against the real Room stack: this is the case the swipe gesture makes reachable
    // from a list, and dropping one leg would leave the other account credited out of nowhere.
    "deleting one leg of a transfer removes both and the undo brings both balances back" {
        val applyChange = ApplyTransactionChangeUseCase(stack.transactionRepository, stack.accountRepository)
        val deleteTransaction = DeleteTransactionUseCase(stack.transactionRepository, applyChange)
        val restoreTransactions = RestoreTransactionsUseCase(applyChange)

        val from = anAccount(
            id = accountId("a-from"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 500.dollars,
        )
        val to = anAccount(
            id = accountId("a-to"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 100.dollars,
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        val transfer = transferId("tr-1")
        stack.createTransaction(
            aTransaction(
                id = transactionId("leg-out"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = from.id,
                money = 80.dollars,
                currencyCode = USD,
                categoryId = null,
                type = TransactionType.EXPENSE,
                transferId = transfer,
            ),
        )
        stack.createTransaction(
            aTransaction(
                id = transactionId("leg-in"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = to.id,
                money = 80.dollars,
                currencyCode = USD,
                categoryId = null,
                type = TransactionType.INCOME,
                transferId = transfer,
            ),
        )
        stack.accountRepository.getById(from.id)!!.balance shouldBe 420.dollars
        stack.accountRepository.getById(to.id)!!.balance shouldBe 180.dollars

        // Swiping either leg deletes the transfer; this one is the leg the user did not open.
        val deleted = deleteTransaction(transactionId("leg-in"))

        deleted.map { it.id }.toSet() shouldBe setOf(transactionId("leg-out"), transactionId("leg-in"))
        stack.transactionRepository.getById(transactionId("leg-out")) shouldBe null
        stack.transactionRepository.getById(transactionId("leg-in")) shouldBe null
        stack.accountRepository.getById(from.id)!!.balance shouldBe 500.dollars
        stack.accountRepository.getById(to.id)!!.balance shouldBe 100.dollars

        restoreTransactions(deleted)

        stack.transactionRepository.getById(transactionId("leg-out")).shouldNotBeNull()
        stack.transactionRepository.getById(transactionId("leg-in")).shouldNotBeNull()
        stack.accountRepository.getById(from.id)!!.balance shouldBe 420.dollars
        stack.accountRepository.getById(to.id)!!.balance shouldBe 180.dollars
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
