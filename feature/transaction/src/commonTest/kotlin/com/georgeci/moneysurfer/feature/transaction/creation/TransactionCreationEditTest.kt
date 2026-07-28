package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.recurringRuleId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar

/**
 * Opening `TransactionCreationViewModel` on an existing row: editing it in place, duplicating it,
 * the identity band it snapshots, and deleting from the edit screen. Plain saves live in
 * [TransactionCreationSaveTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationEditTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "duplicating prefills the fields but saves a brand-new transaction" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-original"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                note = "Lidl — weekly shop",
                type = TransactionType.EXPENSE,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(duplicateOf = original.id)
            try {
                val content = vm.awaitContent()

                content.isEditMode shouldBe false
                content.editingTransactionId shouldBe null
                content.editingCreatedAt shouldBe null
                content.pinnedOperationDate shouldBe null
                content.amount shouldBe "80"
                content.note shouldBe original.note
                content.selectedAccount?.id shouldBe acc.id
                content.selectedCategory?.id shouldBe category.id
                // The original's timestamp is deliberately not copied — a duplicate is entered now.
                content.timestamp shouldNotBe original.operationAt.toEpochMilliseconds()

                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                // `original` was seeded through insert(), so the copy is the second entry.
                val saved = fixture.transactionRepository.inserted.last()
                saved.id shouldNotBe original.id
                saved.money shouldBe original.money
                saved.note shouldBe original.note
                saved.categoryId shouldBe category.id
                fixture.transactionRepository.getById(original.id) shouldBe original
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "editing hands back the fields the form has no input for" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            // Everything here lives outside the form: no field on the screen can express it, so an
            // edit that changes only the amount must leave all of it exactly as it was.
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                merchant = "Lidl",
                tags = listOf("weekly", "food"),
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PLANNED,
                transferId = TransferId("tr-1"),
                splitId = SplitId("sp-1"),
                recurringRuleId = recurringRuleId("r-1"),
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("95"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val updated = fixture.transactionRepository.getById(original.id).shouldNotBeNull()
                updated.money shouldBe 95.dollars
                updated.merchant shouldBe "Lidl"
                updated.tags shouldBe listOf("weekly", "food")
                updated.status shouldBe TransactionStatus.PLANNED
                // Dropping this would orphan the transfer's other leg.
                updated.transferId shouldBe TransferId("tr-1")
                // …and dropping this would leave the receipt's other legs with no collapsed row to
                // reveal them, while this one silently left the group.
                updated.splitId shouldBe SplitId("sp-1")
                updated.recurringRuleId shouldBe recurringRuleId("r-1")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "editing snapshots the stored row for the identity band and keeps it while fields change" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-8213"),
                workspaceId = ws,
                accountId = acc.id,
                money = 48.dollars,
                categoryId = category.id,
                note = "Lidl — weekly shop",
                type = TransactionType.EXPENSE,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                val identity = vm.awaitContent().editIdentity.shouldNotBeNull()
                identity.reference shouldBe original.id.reference
                identity.type shouldBe TransactionTypeUi.Expense
                identity.note shouldBe original.note
                identity.formattedAmount shouldBe "−$48.00"

                // The band answers "which transaction is this", so editing must not rewrite it.
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("95"))
                vm.onEvent(TransactionCreationEvent.OnNoteChanged("something else"))

                vm.awaitContent().editIdentity shouldBe identity
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the identity band calls a transfer leg a transfer and leaves its amount unsigned" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            // Stored as an ordinary EXPENSE, as both legs of a transfer are — only the transferId
            // says the money moved sideways rather than left.
            val leg = aTransaction(
                id = transactionId("t-leg"),
                workspaceId = ws,
                accountId = acc.id,
                money = 200.dollars,
                categoryId = category.id,
                note = "Rainy day top-up",
                type = TransactionType.EXPENSE,
                transferId = TransferId("tr-1"),
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(leg)
            }
            val vm = fixture.createViewModel(editingTransactionId = leg.id)
            try {
                val identity = vm.awaitContent().editIdentity.shouldNotBeNull()
                identity.type shouldBe TransactionTypeUi.Transfer
                // Neither "+" nor "−": the money neither arrived nor left, matching the details screen.
                identity.formattedAmount shouldBe "$200.00"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "there is no identity band outside edit mode" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                money = 48.dollars,
                categoryId = category.id,
                type = TransactionType.EXPENSE,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val duplicating = fixture.createViewModel(duplicateOf = original.id)
            try {
                duplicating.awaitContent().editIdentity shouldBe null
            } finally {
                duplicating.viewModelScope.cancel()
            }
        }
    }

    "deleting from edit mode removes the row, refunds the balance and offers undo" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                type = TransactionType.EXPENSE,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnDeleteClick)
                vm.awaitContent().showDeleteConfirmation shouldBe true

                fixture.snackbar.requests.test {
                    vm.onEvent(TransactionCreationEvent.OnDeleteConfirmed)

                    val request = awaitItem()
                    request.message shouldBe Res.string.transaction_details_deleted_snackbar
                    request.actionLabel shouldBe Res.string.transaction_details_delete_undo
                    fixture.transactionRepository.getById(original.id) shouldBe null
                    fixture.accountRepository.byId[acc.id]!!.balance shouldBe 580.dollars

                    // Undo is the same restore the details screen offers — row and balance both.
                    request.onAction.shouldNotBeNull().invoke()
                    fixture.transactionRepository.getById(original.id) shouldBe original
                    fixture.accountRepository.byId[acc.id]!!.balance shouldBe 500.dollars
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "dismissing the delete confirmation leaves the transaction alone" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                type = TransactionType.EXPENSE,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnDeleteClick)
                vm.onEvent(TransactionCreationEvent.OnDeleteDismissed)

                vm.awaitContent().showDeleteConfirmation shouldBe false
                fixture.transactionRepository.getById(original.id) shouldBe original
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "duplicating carries the merchant and tags but not the pairing, schedule or planned state" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-original"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                merchant = "Lidl",
                tags = listOf("weekly"),
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PLANNED,
                transferId = TransferId("tr-1"),
                splitId = SplitId("sp-1"),
                recurringRuleId = recurringRuleId("r-1"),
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            // The details screen hides Duplicate for a transfer leg, so this asserts the VM is
            // defensive rather than describing a reachable flow.
            val vm = fixture.createViewModel(duplicateOf = original.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val copy = fixture.transactionRepository.inserted.last()
                copy.id shouldNotBe original.id
                copy.merchant shouldBe "Lidl"
                copy.tags shouldBe listOf("weekly")
                copy.status shouldBe TransactionStatus.ACTUAL
                copy.transferId shouldBe null
                // Same reason: a duplicate must not attach itself as an extra leg of a receipt
                // the user is only using as a template.
                copy.splitId shouldBe null
                copy.recurringRuleId shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})
