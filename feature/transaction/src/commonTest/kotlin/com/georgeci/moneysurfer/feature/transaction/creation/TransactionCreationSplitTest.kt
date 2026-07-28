package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.lifecycle.viewModelScope
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The split editor: turning one payment into several categorized legs.
 *
 * The invariant worth protecting here is that the legs always add up to the amount the user
 * entered. The editor gets that by construction — the last line carries the remainder — so these
 * cases mostly check that the construction holds when lines are added, removed and re-typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationSplitTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    fun fixture(): TransactionCreationFixture {
        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = ws,
            currencyCode = USD,
            balance = 500.dollars,
        )
        return TransactionCreationFixture(ws).apply {
            accountRepository.seed(account)
            categoryRepository.seed(
                aCategory(id = categoryId("c-food"), workspaceId = ws, type = CategoryType.EXPENSE),
                aCategory(id = categoryId("c-home"), workspaceId = ws, type = CategoryType.EXPENSE),
            )
        }
    }

    "saving a split writes one leg per line, sharing a split id" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                val content = vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val lines = vm.content().splitLines
                // The first line inherits the form's category; the second is picked by hand.
                content.selectedCategory.shouldNotBeNull()
                vm.onEvent(
                    TransactionCreationEvent.OnOpenSplitLineCategoryChooser(lines.last().key),
                )
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(categoryId("c-home")))
                vm.onEvent(
                    TransactionCreationEvent.OnSplitLineAmountChanged(lines.first().key, "30"),
                )

                vm.onEvent(TransactionCreationEvent.OnSaveClick)

                val written = fixture.transactionRepository.inserted
                written shouldHaveSize 2
                written.map { it.money } shouldBe listOf(30.dollars, 4.dollars)
                written.last().categoryId shouldBe categoryId("c-home")
                written.map { it.splitId }.distinct() shouldHaveSize 1
                written.first().splitId.shouldNotBeNull()
                written.map { it.type }.distinct() shouldBe listOf(TransactionType.EXPENSE)
                // The account is charged the payment, not one of its parts.
                fixture.accountRepository.byId[accountId("a-1")]!!.balance shouldBe 466.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the trailing line always carries what is left of the amount" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("50"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "20"))

                vm.content().splitRemainder shouldBe 30.dollars

                // Adding a third line takes its share from the remainder, never from the total.
                vm.onEvent(TransactionCreationEvent.OnSplitLineAdded)
                val second = vm.content().splitLines[1].key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(second, "5"))

                vm.content().splitRemainder shouldBe 25.dollars
                vm.content().splitAmounts shouldBe listOf(20.dollars, 5.dollars, 25.dollars)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "assigning more than the amount blocks the save" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("10"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "12"))

                vm.content().splitRemainder shouldBe Money.fromMinor(-200)
                vm.content().isSaveEnabled shouldBe false

                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                fixture.transactionRepository.inserted shouldHaveSize 0
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a line left without a category blocks the save" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "30"))

                // The second line is seeded empty and nothing was picked for it.
                vm.content().splitLines.last().category shouldBe null
                vm.content().isSaveEnabled shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "dropping back below two lines leaves the split editor" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                vm.onEvent(
                    TransactionCreationEvent.OnSplitLineRemoved(vm.content().splitLines.last().key),
                )

                vm.content().isSplit shouldBe false
                // Back to the ordinary single-category form, which saves one row.
                vm.onEvent(TransactionCreationEvent.OnSaveClick)
                fixture.transactionRepository.inserted shouldHaveSize 1
                fixture.transactionRepository.inserted.single().splitId shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "switching the type drops split categories the new type cannot use" {
        runTest {
            val fixture = fixture()
            // An income category exists, so the type switch has somewhere to land.
            fixture.categoryRepository.seed(
                aCategory(id = categoryId("c-salary"), workspaceId = ws, type = CategoryType.INCOME),
            )
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "30"))
                vm.content().splitLines.first().category?.type shouldBe CategoryType.EXPENSE

                vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))

                // Keeping the expense category would file income under it — money the spend
                // aggregates never see, on a category screen that would then list income.
                vm.content().splitLines.map { it.category } shouldBe listOf(null, null)
                // The lines and their amounts survive; only the categories are dropped.
                vm.content().splitLines.first().amount shouldBe "30"
                vm.content().isSaveEnabled shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "adding a line keeps the amount the trailing line was showing" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "30"))
                // Line 2 is showing the remainder, 4.00, without storing it.
                vm.content().splitRemainder shouldBe 4.dollars

                vm.onEvent(TransactionCreationEvent.OnSplitLineAdded)

                // It keeps the 4.00 the user could see rather than coming back blank and leaving a
                // zero leg that only disables Save.
                vm.content().splitLines[1].amount shouldBe "4"
                vm.content().splitAmounts shouldBe listOf(30.dollars, 4.dollars, Money.zero())
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "an over-assigned remainder is not pinned onto the line that loses its place" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("10"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val first = vm.content().splitLines.first().key
                vm.onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(first, "12"))

                vm.onEvent(TransactionCreationEvent.OnSplitLineAdded)

                // Storing the negative remainder would come back through abs() as +2.00 — a figure
                // the user never entered.
                vm.content().splitLines[1].amount shouldBe ""
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "picking a category for a line leaves the form's own category alone" {
        runTest {
            val fixture = fixture()
            val vm = fixture.createViewModel(prefillAccount = accountId("a-1"))
            try {
                vm.awaitContent()
                vm.onEvent(TransactionCreationEvent.OnAmountChanged("34"))
                vm.onEvent(TransactionCreationEvent.OnSplitToggled)
                val before = vm.content().selectedCategory

                vm.onEvent(
                    TransactionCreationEvent.OnOpenSplitLineCategoryChooser(
                        vm.content().splitLines.last().key,
                    ),
                )
                vm.onEvent(TransactionCreationEvent.OnCategoryPicked(categoryId("c-home")))

                vm.content().selectedCategory shouldBe before
                vm.content().splitLines.last().category?.id shouldBe categoryId("c-home")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private fun TransactionCreationViewModel.content(): TransactionCreationState.Content =
    currentState as TransactionCreationState.Content
