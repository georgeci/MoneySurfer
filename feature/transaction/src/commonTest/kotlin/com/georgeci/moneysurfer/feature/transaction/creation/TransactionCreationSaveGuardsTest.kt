package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * What Save refuses to write.
 *
 * Every case here is a form the button is disabled for, sent anyway: the state's `isSaveEnabled` is
 * the screen's business, and a ViewModel that trusted it would write a half-filled transaction the
 * moment a stale click, a restored process or a test harness got past the button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationSaveGuardsTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "a workspace with no account has nothing to save against" {
        runTest {
            val fixture = TransactionCreationFixture(ws).apply {
                categoryRepository.seed(aCategory(id = categoryId("c-exp"), workspaceId = ws))
            }
            val vm = fixture.createViewModel()
            try {
                val content = vm.awaitContent()
                content.selectedAccount shouldBe null
                content.isSaveEnabled shouldBe false

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a workspace with no category of this type has nothing to file the row under" {
        runTest {
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars))
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent().selectedCategory shouldBe null

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an empty amount is not a transaction" {
        runTest {
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars))
                categoryRepository.seed(aCategory(id = categoryId("c-exp"), workspaceId = ws))
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a transfer with only one side filled writes neither leg" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from) }
            val vm = fixture.createViewModel(openAsTransfer = true)
            try {
                // One account in the workspace: the To slot has nothing to be seeded with.
                vm.awaitContent().toAccount shouldBe null

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
                fixture.accountRepository.byId.getValue(from.id).balance shouldBe 500.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a transfer into the account the money leaves is not a transfer" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val to = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from, to) }
            val vm = fixture.createViewModel(openAsTransfer = true)
            try {
                vm.awaitContent()

                fixture.pickToAccount(vm, from.id)
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a transfer needs a positive amount on the side the money leaves" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val to = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from, to) }
            val vm = fixture.createViewModel(openAsTransfer = true)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("0"))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a cross-currency transfer needs the receiving amount too" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val to = anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR, name = "Savings")
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from, to) }
            val vm = fixture.createViewModel(openAsTransfer = true)
            try {
                vm.awaitContent().crossCurrency shouldBe true

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                // The receiving leg has no rate to derive its amount from, so a blank field is not
                // "the same number" — it is a leg nobody entered.
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a split with no account to charge writes no legs" {
        runTest {
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = TransactionCreationFixture(ws).apply { categoryRepository.seed(category) }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent().selectedAccount shouldBe null

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val lines = vm.awaitContent().splitLines
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(lines.first().key, "40"))
                vm.onEvent(TransactionCreationEvent.OnOpenSplitLineCategoryChooser(lines.last().key))
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(category.id))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a split entered on the Income tab writes income legs" {
        runTest {
            val account = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val salary = aCategory(
                id = categoryId("c-inc"),
                workspaceId = ws,
                type = CategoryType.INCOME,
                name = "Salary",
            )
            val bonus = aCategory(
                id = categoryId("c-inc-2"),
                workspaceId = ws,
                type = CategoryType.INCOME,
                name = "Bonus",
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(account)
                categoryRepository.seed(salary, bonus)
            }
            val vm = fixture.createViewModel(prefillAccount = account.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val lines = vm.awaitContent().splitLines
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(lines.first().key, "60"))
                vm.onEvent(TransactionCreationEvent.OnOpenSplitLineCategoryChooser(lines.last().key))
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(bonus.id))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val legs = fixture.transactionRepository.inserted
                legs.map { it.type } shouldBe listOf(TransactionType.INCOME, TransactionType.INCOME)
                legs.map { it.money } shouldBe listOf(60.dollars, 40.dollars)
                // Income adds up rather than draining the account, which is the half of the type
                // that a split used to get wrong.
                fixture.accountRepository.byId.getValue(account.id).balance shouldBe 600.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "there is nothing to delete while a transaction is still being entered" {
        runTest {
            val account = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(account)
                categoryRepository.seed(aCategory(id = categoryId("c-exp"), workspaceId = ws))
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent().editingTransactionId shouldBe null

                // The screen hides the action here; confirming it anyway must not reach the undo
                // snackbar, which would offer to restore a row that was never written.
                vm.onEvent(TransactionCreationEvent.OnDeleteConfirmed)

                fixture.accountRepository.byId.getValue(account.id).balance shouldBe 500.dollars
                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})
