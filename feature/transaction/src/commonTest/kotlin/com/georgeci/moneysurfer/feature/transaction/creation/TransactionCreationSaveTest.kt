package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_created_snackbar

/**
 * Save flows for `TransactionCreationViewModel` — plain income/expense, amount validation and
 * transfers. Edit, duplicate and delete live in [TransactionCreationEditTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationSaveTest : StringSpec({

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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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
            val fixture = TransactionCreationFixture(ws).apply {
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

    "Transfer is unreachable when transferEnabled flag is off" {
        runTest {
            val fixture = TransactionCreationFixture(ws)
            val vm = fixture.createViewModel(
                featureConfig = TransactionCreationFeatureConfig(transferEnabled = false),
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

    "Transfer is reachable when transferEnabled flag is on" {
        runTest {
            val fixture = TransactionCreationFixture(ws)
            val vm = fixture.createViewModel(
                featureConfig = TransactionCreationFeatureConfig(transferEnabled = true),
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
})
