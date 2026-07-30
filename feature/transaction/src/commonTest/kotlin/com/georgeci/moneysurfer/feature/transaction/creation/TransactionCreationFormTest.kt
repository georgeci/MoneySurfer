package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The form half of `TransactionCreationViewModel`: the fields the screen edits directly, the four
 * chooser destinations it opens, and what a chooser's answer does to the slot it was opened for.
 * Saving lives in [TransactionCreationSaveTest], the split editor in
 * [TransactionCreationSplitEditingTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationFormTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "picking a date replaces the business date the edited row was pinned to" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 500.dollars)
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                money = 80.dollars,
                categoryId = category.id,
                operationDate = LocalDate(2025, 1, 5),
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(category)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                vm.awaitContent().pinnedOperationDate shouldBe LocalDate(2025, 1, 5)

                val picked = LocalDate(2025, 3, 18)
                vm.onEvent(TransactionCreationEvent.OnDateChanged(picked.atNoon()))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                // Unpinning is the point: the stored date must follow the date the user just chose
                // rather than the one the row was opened with.
                vm.awaitContent().pinnedOperationDate shouldBe null
                fixture.transactionRepository.getById(original.id)?.operationDate shouldBe picked
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "Today puts the form back on the current date and unpins the stored one" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                categoryId = null,
                operationDate = LocalDate(2025, 1, 5),
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                val opened = vm.awaitContent()
                opened.pinnedOperationDate shouldBe LocalDate(2025, 1, 5)

                vm.onEvent(TransactionCreationEvent.OnTodayClick)

                val content = vm.awaitContent()
                content.timestamp shouldNotBe opened.timestamp
                content.pinnedOperationDate shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "swapping the transfer accounts carries each side's amount with it" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD)
            val to = anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR)
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from, to) }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
                fixture.pickFromAccount(vm, from.id)
                fixture.pickToAccount(vm, to.id)
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
                vm.onEvent(TransactionCreationEvent.OnToAmountChanged("92"))

                vm.onEvent(TransactionCreationEvent.OnSwapAccountsClick)

                val content = vm.awaitContent()
                content.fromAccount?.id shouldBe to.id
                content.toAccount?.id shouldBe from.id
                // An amount left behind on the other side would quietly change the rate.
                content.amount shouldBe "92"
                content.toAmount shouldBe "100"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an account or a category handed straight to the form replaces its selection" {
        runTest {
            val first = anAccount(id = accountId("a-1"), workspaceId = ws)
            val second = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val other = aCategory(
                id = categoryId("c-exp-2"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
                name = "Transport",
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(first, second)
                categoryRepository.seed(category, other)
            }
            val vm = fixture.createViewModel(prefillAccount = first.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnAccountSelected(second))
                vm.onEvent(TransactionCreationEvent.OnCategorySelected(other))

                val content = vm.awaitContent()
                content.selectedAccount?.id shouldBe second.id
                content.selectedCategory?.id shouldBe other.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the category chooser opens on the form's own category, filtered to the type's side" {
        runTest {
            val category = aCategory(id = categoryId("c-inc"), workspaceId = ws, type = CategoryType.INCOME)
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws))
                categoryRepository.seed(category)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenCategoryChooser)
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToCategoryChooser(
                        selectedCategoryId = category.id,
                        filterType = CategoryType.INCOME,
                    )
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a split line's chooser opens on that line's category rather than on the form's" {
        runTest {
            val formCategory = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val lineCategory = aCategory(
                id = categoryId("c-exp-2"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
                name = "Transport",
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws))
                categoryRepository.seed(formCategory, lineCategory)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val secondLine = vm.awaitContent().splitLines.last().key

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenSplitLineCategoryChooser(secondLine))
                    // The line starts empty even though the form itself has a category.
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToCategoryChooser(
                        selectedCategoryId = null,
                        filterType = CategoryType.EXPENSE,
                    )

                    vm.onEvent(TransactionCreationEvent.OnCategoryPicked(lineCategory.id))
                    vm.onEvent(TransactionCreationEvent.OnOpenSplitLineCategoryChooser(secondLine))
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToCategoryChooser(
                        selectedCategoryId = lineCategory.id,
                        filterType = CategoryType.EXPENSE,
                    )
                }

                // …and the pick landed on the line, not on the form's own category.
                val content = vm.awaitContent()
                content.selectedCategory?.id shouldBe formCategory.id
                content.splitLines.last().category?.id shouldBe lineCategory.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the single-account chooser offers the transfer shortcut; the transfer slots exclude each other" {
        runTest {
            val from = anAccount(id = accountId("a-1"), workspaceId = ws)
            val to = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(from, to) }
            val vm = fixture.createViewModel(prefillAccount = from.id)
            try {
                vm.awaitContent()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToAccountChooser(
                        selectedAccountId = from.id,
                        excludeAccountId = null,
                        showTransferShortcut = true,
                    )
                }

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenToAccountChooser)
                    // The From slot is already filled, so offering it again could only ever produce
                    // a transfer into the account the money is leaving.
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToAccountChooser(
                        selectedAccountId = to.id,
                        excludeAccountId = from.id,
                        showTransferShortcut = false,
                    )

                    vm.onEvent(TransactionCreationEvent.OnOpenFromAccountChooser)
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToAccountChooser(
                        selectedAccountId = from.id,
                        excludeAccountId = to.id,
                        showTransferShortcut = false,
                    )
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "editing an existing row is not a way into a transfer, so its chooser offers no shortcut" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws)
            val other = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
            val original = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = acc.id,
                categoryId = null,
            )
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(acc, other)
                transactionRepository.insert(original)
            }
            val vm = fixture.createViewModel(editingTransactionId = original.id)
            try {
                vm.awaitContent()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                    awaitItem().shouldBeInstanceOf<TransactionCreationEffect.NavigateToAccountChooser>()
                        .showTransferShortcut shouldBe false
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a build without transfers offers no shortcut either" {
        runTest {
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(
                    anAccount(id = accountId("a-1"), workspaceId = ws),
                    anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings"),
                )
            }
            val vm = fixture.createViewModel(
                hostCapabilities = FakeHostCapabilities(transferEnabled = false),
            )
            try {
                vm.awaitContent()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                    awaitItem().shouldBeInstanceOf<TransactionCreationEffect.NavigateToAccountChooser>()
                        .showTransferShortcut shouldBe false
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the form's two plain navigation actions leave the state alone" {
        runTest {
            val fixture = fixtureWithOneAccount(ws)
            val vm = fixture.createViewModel()
            try {
                val before = vm.awaitContent()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionCreationEvent.OnOpenCategoryCreation)
                    awaitItem() shouldBe TransactionCreationEffect.NavigateToCategoryCreation

                    vm.onEvent(TransactionCreationEvent.OnBackClick)
                    awaitItem() shouldBe TransactionCreationEffect.NavigateBack
                }

                vm.awaitContent() shouldBe before
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an account created while the form was open is resolved by a refresh" {
        runTest {
            val first = anAccount(id = accountId("a-1"), workspaceId = ws)
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(first) }
            val vm = fixture.createViewModel(prefillAccount = first.id)
            try {
                vm.awaitContent().accounts.map { it.id } shouldBe listOf(first.id)

                // The chooser can create an account and hand back its id — the form loaded its list
                // before that account existed, so a stale list must not swallow the pick.
                val created = anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings")
                fixture.accountRepository.seed(created)
                vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                vm.onEvent(TransactionCreationEvent.OnAccountPicked(created.id))

                val content = vm.awaitContent()
                content.accounts.map { it.id } shouldBe listOf(first.id, created.id)
                content.selectedAccount?.id shouldBe created.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an account that a refresh cannot find leaves the slot as it was" {
        runTest {
            val first = anAccount(id = accountId("a-1"), workspaceId = ws)
            val fixture = TransactionCreationFixture(ws).apply { accountRepository.seed(first) }
            val vm = fixture.createViewModel(prefillAccount = first.id)
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                vm.onEvent(TransactionCreationEvent.OnAccountPicked(accountId("a-gone")))

                vm.awaitContent().selectedAccount?.id shouldBe first.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a category created while the form was open is resolved by a refresh" {
        runTest {
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws))
                categoryRepository.seed(category)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                val created = aCategory(
                    id = categoryId("c-new"),
                    workspaceId = ws,
                    type = CategoryType.EXPENSE,
                    name = "Coffee",
                )
                fixture.categoryRepository.seed(created)
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(created.id))

                val content = vm.awaitContent()
                content.selectedCategory?.id shouldBe created.id
                content.displayCategories.map { it.id } shouldBe listOf(category.id, created.id)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a category that a refresh cannot find leaves the form's own category selected" {
        runTest {
            val category = aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE)
            val fixture = TransactionCreationFixture(ws).apply {
                accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws))
                categoryRepository.seed(category)
            }
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()

                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(categoryId("c-gone")))

                vm.awaitContent().selectedCategory?.id shouldBe category.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "events that arrive before the form has loaded are ignored" {
        runTest {
            // A standard dispatcher keeps `loadData()` queued, which is the only way to observe the
            // screen in the state a fast tap on a cold start actually finds it in.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = fixtureWithOneAccount(ws)
            val vm = fixture.createViewModel()
            // Collected on an *unconfined* dispatcher while Main stays standard: anything the
            // ViewModel posts is delivered the moment it is posted, so the assertion below fails on
            // a dropped guard. A collector on the queued Main dispatcher would receive nothing
            // either way, and the case would pass whatever the ViewModel did.
            val effects = mutableListOf<TransactionCreationEffect>()
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.sideEffects.effectFlow.toList(effects)
            }
            try {
                vm.currentState shouldBe TransactionCreationState.Loading

                vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))
                vm.onEvent(TransactionCreationEvent.OnAccountPicked(accountId("a-1")))
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(categoryId("c-exp")))
                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                vm.onEvent(TransactionCreationEvent.OnDeleteConfirmed)
                // The two choosers have no form to open on, so neither posts anywhere.
                vm.onEvent(TransactionCreationEvent.OnOpenAccountChooser)
                vm.onEvent(TransactionCreationEvent.OnOpenCategoryChooser)

                effects.shouldBeEmpty()
                vm.currentState shouldBe TransactionCreationState.Loading
                fixture.transactionRepository.inserted.shouldBeEmpty()

                advanceUntilIdle()
                vm.currentState.shouldBeInstanceOf<TransactionCreationState.Content>()
            } finally {
                collector.cancel()
                vm.viewModelScope.cancel()
            }
        }
    }
})

private fun fixtureWithOneAccount(ws: WorkspaceId) = TransactionCreationFixture(ws).apply {
    accountRepository.seed(anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars))
    categoryRepository.seed(aCategory(id = categoryId("c-exp"), workspaceId = ws, type = CategoryType.EXPENSE))
}

/**
 * Midday rather than midnight: the form stores an instant and reads the business date back in the
 * system zone, so a boundary hour would make the assertion depend on where the test runs.
 */
private fun LocalDate.atNoon(): Long =
    atTime(LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
