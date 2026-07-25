package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
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
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_created_snackbar
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar

/**
 * Save flows for `TransactionCreationViewModel`. Each test stages accounts +
 * categories so the VM's `loadData()` resolves a default selection, then drives
 * the relevant `On*` events and asserts on the fake repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "save EXPENSE persists a transaction with the account's currency and updates balance" {
        runTest {
            val acc = anAccount(
                id = accountId("a-1"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 500.dollars,
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val saved = fixture.transactionRepository.inserted.single()
                saved.type shouldBe TransactionType.EXPENSE
                saved.money shouldBe 80.dollars
                saved.currencyCode shouldBe USD
                saved.accountId shouldBe acc.id
                saved.categoryId shouldBe expenseCategory.id

                fixture.accountRepository.byId[acc.id]!!.balance shouldBe 420.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save EXPENSE shows a created snackbar" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val expenseCategory = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)
            try {
                vm.awaitContent()
                fixture.snackbar.requests.test {
                    vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
                    vm.onEvent(TransactionCreationEvent.OnSaveClick)
                    awaitItem().message shouldBe Res.string.transaction_creation_created_snackbar
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "switching to Income picks an income category and save persists INCOME" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD)
            val incomeCategory = aCategory(
                id = categoryId("c-inc"),
                workspaceId = ws,
                type = CategoryType.INCOME,
                name = "Salary",
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory, incomeCategory)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("125"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val saved = fixture.transactionRepository.inserted.single()
                saved.type shouldBe TransactionType.INCOME
                saved.money shouldBe 125.dollars
                saved.categoryId shouldBe incomeCategory.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a zero amount blocks save and exposes an inline error" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val expenseCategory = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("0"))

                val state = vm.awaitContent()
                state.amountError shouldBe TransactionAmountError.NOT_POSITIVE
                state.isSaveEnabled shouldBe false

                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                fixture.transactionRepository.inserted.size shouldBe 0
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an amount with two decimal separators blocks save and exposes an inline error" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val expenseCategory = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("1.2.3"))

                val state = vm.awaitContent()
                state.amountError shouldBe TransactionAmountError.INVALID_FORMAT
                state.isSaveEnabled shouldBe false

                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                fixture.transactionRepository.inserted.size shouldBe 0
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a comma decimal separator is accepted and the parsed amount is persisted" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val expenseCategory = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("12,50"))

                val state = vm.awaitContent()
                state.amountError shouldBe null
                state.isSaveEnabled shouldBe true

                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                fixture.transactionRepository.inserted.single().money shouldBe Money.fromDouble(12.50)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "same-currency transfer creates two paired legs with equal amounts" {
        runTest {
            val from = anAccount(
                id = accountId("a-from"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 1_000.dollars,
            )
            val to = anAccount(
                id = accountId("a-to"),
                workspaceId = ws,
                currencyCode = USD,
                balance = Money.zero(),
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(from, to)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("150"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val txns = fixture.transactionRepository.inserted
                txns.size shouldBe 2
                val expense = txns.single { it.type == TransactionType.EXPENSE }
                val income = txns.single { it.type == TransactionType.INCOME }
                expense.accountId shouldBe from.id
                income.accountId shouldBe to.id
                expense.money shouldBe 150.dollars
                income.money shouldBe 150.dollars
                expense.transferId shouldNotBe null
                expense.transferId shouldBe income.transferId

                fixture.accountRepository.byId[from.id]!!.balance shouldBe 850.dollars
                fixture.accountRepository.byId[to.id]!!.balance shouldBe 150.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "cross-currency transfer uses separate fromMoney and toMoney with each account's currency" {
        runTest {
            val from = anAccount(
                id = accountId("a-usd"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 500.dollars,
            )
            val to = anAccount(
                id = accountId("a-eur"),
                workspaceId = ws,
                currencyCode = EUR,
                balance = Money.zero(),
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(from, to)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
                // The VM may seed fromAccount=first and toAccount=second; force the order explicitly.
                fixture.pickFromAccount(vm, from.id)
                fixture.pickToAccount(vm, to.id)
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnToAmountChanged("92"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val expense = fixture.transactionRepository.inserted.single { it.type == TransactionType.EXPENSE }
                val income = fixture.transactionRepository.inserted.single { it.type == TransactionType.INCOME }
                expense.money shouldBe 100.dollars
                expense.currencyCode shouldBe USD
                income.money shouldBe 92.dollars
                income.currencyCode shouldBe EUR

                fixture.accountRepository.byId[from.id]!!.balance shouldBe 400.dollars
                fixture.accountRepository.byId[to.id]!!.balance shouldBe 92.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "Transfer is unreachable when the host disables it" {
        runTest {
            val fixture = Fixture(ws)
            val vm = fixture.createViewModel(
                hostCapabilities = FakeHostCapabilities(transferEnabled = false),
            )
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))

                val state = vm.awaitContent()
                state.transferEnabled shouldBe false
                state.isTransfer shouldBe false
                state.type shouldBe TransactionTypeUi.Expense
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "Transfer is reachable when the host enables it" {
        runTest {
            val fixture = Fixture(ws)
            val vm = fixture.createViewModel(
                hostCapabilities = FakeHostCapabilities(transferEnabled = true),
            )
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))

                val state = vm.first {
                    it is TransactionCreationState.Content && it.type == TransactionTypeUi.Transfer
                } as TransactionCreationState.Content
                state.transferEnabled shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

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
            val fixture = Fixture(ws).apply {
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
                recurringRuleId = recurringRuleId("r-1"),
            )
            val fixture = Fixture(ws).apply {
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
            val fixture = Fixture(ws).apply {
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
            val fixture = Fixture(ws).apply {
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
            val fixture = Fixture(ws).apply {
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
            val fixture = Fixture(ws).apply {
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
            val fixture = Fixture(ws).apply {
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
                recurringRuleId = recurringRuleId("r-1"),
            )
            val fixture = Fixture(ws).apply {
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
                copy.recurringRuleId shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

/** Wait until the VM finishes its async `loadData()` and exposes a `Content` state.
 * Sending events before that point would hit the `Loading` reducers and silently no-op. */
private suspend fun TransactionCreationViewModel.awaitContent(): TransactionCreationState.Content =
    first { it is TransactionCreationState.Content } as TransactionCreationState.Content

private class Fixture(workspaceId: WorkspaceId) {
    val accountRepository = FakeAccountRepository()
    val transactionRepository = FakeTransactionRepository()
    val categoryRepository = FakeCategoryRepository()
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val snackbar = SnackbarController()
    private val clock = ClockUseCase()
    private val applyChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)

    fun createViewModel(
        editingTransactionId: TransactionId? = null,
        duplicateOf: TransactionId? = null,
        prefillAccount: AccountId? = null,
        hostCapabilities: HostCapabilities = FakeHostCapabilities(),
    ) = TransactionCreationViewModel(
        seed = duplicateOf?.let {
            TransactionCreationSeed(it, TransactionCreationSeed.Mode.Duplicate)
        } ?: editingTransactionId?.let {
            TransactionCreationSeed(it, TransactionCreationSeed.Mode.Edit)
        },
        accountId = prefillAccount,
        getAccounts = GetAccountsUseCase(accountRepository, session),
        getCategories = GetCategoriesUseCase(categoryRepository, session),
        getTransactionById = GetTransactionByIdUseCase(transactionRepository),
        createTransaction = CreateTransactionUseCase(applyChange),
        updateTransaction = UpdateTransactionUseCase(transactionRepository, applyChange),
        createTransfer = CreateTransferUseCase(
            categoryRepository = categoryRepository,
            applyTransactionChange = applyChange,
            getCurrentTime = GetCurrentTimeUseCase(clock),
        ),
        deleteTransaction = DeleteTransactionUseCase(transactionRepository, applyChange),
        applyTransactionChange = applyChange,
        getCurrentTime = GetCurrentTimeUseCase(clock),
        transactionRepository = transactionRepository,
        hostCapabilities = hostCapabilities,
        snackbar = snackbar,
    )

    fun pickFromAccount(vm: TransactionCreationViewModel, id: AccountId) {
        vm.onEvent(TransactionCreationEvent.OnOpenFromAccountChooser)
        vm.onEvent(TransactionCreationEvent.OnAccountPicked(id))
    }

    fun pickToAccount(vm: TransactionCreationViewModel, id: AccountId) {
        vm.onEvent(TransactionCreationEvent.OnOpenToAccountChooser)
        vm.onEvent(TransactionCreationEvent.OnAccountPicked(id))
    }
}

private class FakeAccountRepository : AccountRepository {
    val byId: MutableMap<AccountId, Account> = mutableMapOf()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())

    fun seed(vararg accounts: Account) {
        accounts.forEach { byId[it.id] = it }
        byWorkspace.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Account>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = byWorkspace
    override suspend fun getById(id: AccountId): Account? = byId[id]
    override suspend fun insert(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun update(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun delete(id: AccountId) {
        byId.remove(id)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = current.balance + delta)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = balance)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(archived = archived)
        byWorkspace.value = byId.values.toList()
    }
}

private class FakeTransactionRepository : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private val byId = mutableMapOf<TransactionId, Transaction>()
    private val all = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = all
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = all
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ) = error("not used")
    override fun getTotals(accountId: AccountId?, window: TransactionPeriodWindow) = error("not used")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = all
    override suspend fun getById(id: TransactionId): Transaction? = byId[id]
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        byId.values.filter { it.transferId == transferId }
    override suspend fun insert(transaction: Transaction) {
        inserted += transaction
        byId[transaction.id] = transaction
        all.value = byId.values.toList()
    }
    override suspend fun update(transaction: Transaction) {
        byId[transaction.id] = transaction
        all.value = byId.values.toList()
    }
    override suspend fun delete(id: TransactionId) {
        byId.remove(id)
        all.value = byId.values.toList()
    }
}

private class FakeCategoryRepository : CategoryRepository {
    private val byId = mutableMapOf<CategoryId, Category>()
    private val byWorkspace = MutableStateFlow<List<Category>>(emptyList())

    fun seed(vararg categories: Category) {
        categories.forEach { byId[it.id] = it }
        byWorkspace.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Category>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = byWorkspace
    override suspend fun getById(id: CategoryId): Category? = byId[id]
    override suspend fun insert(category: Category) {
        byId[category.id] = category
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun update(category: Category) {
        byId[category.id] = category
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun delete(id: CategoryId) {
        byId.remove(id)
        byWorkspace.value = byId.values.toList()
    }
}
