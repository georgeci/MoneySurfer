package com.georgeci.moneysurfer.feature.budget.list

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.usecase.ArchiveBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.feature.budget.FakeBudgetRepository
import com.georgeci.moneysurfer.feature.budget.FakeCategoryRepository
import com.georgeci.moneysurfer.feature.budget.FakeTransactionRepository
import com.georgeci.moneysurfer.feature.budget.FakeWorkspaceRepository
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budgets_archive_undo
import moneysurfer.feature.budget.generated.resources.budgets_archived_snackbar

private val workspace = workspaceId("ws-1")

/** Today in the default zone — the same date the view model's clock resolves to. */
private fun today(): LocalDate =
    ClockUseCase().now().toLocalDateTime(TimeZone.currentSystemDefault()).date

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "an empty workspace lands on empty Content, not on a stuck spinner" {
        val fixture = fixture()

        val content = fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>()
        content.active shouldBe emptyList()
        content.archived shouldBe emptyList()
    }

    "splits budgets into active and archived" {
        val fixture = fixture()

        fixture.budgets.emit(
            listOf(
                aBudget(id = budgetId("b-1"), workspaceId = workspace, name = "Groceries", startDate = today()),
                aBudget(
                    id = budgetId("b-2"),
                    workspaceId = workspace,
                    name = "Holidays",
                    startDate = today(),
                    isActive = false,
                ),
            ),
        )

        val content = fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>()
        content.active.map { it.name } shouldBe listOf("Groceries")
        content.archived.map { it.name } shouldBe listOf("Holidays")
    }

    "a card carries the spend of the current window, transfers excluded" {
        val fixture = fixture(
            transactions = listOf(
                aTransaction(
                    id = transactionId("t-1"),
                    workspaceId = workspace,
                    money = 300.dollars,
                    currencyCode = USD,
                    operationDate = today(),
                ),
                aTransaction(
                    id = transactionId("t-2"),
                    workspaceId = workspace,
                    money = 500.dollars,
                    currencyCode = USD,
                    operationDate = today(),
                    transferId = TransferId("tr-1"),
                ),
            ),
        )

        fixture.budgets.emit(
            listOf(
                aBudget(
                    id = budgetId("b-1"),
                    workspaceId = workspace,
                    amount = 400.dollars,
                    categoryIds = emptyList(),
                    startDate = today(),
                ),
            ),
        )

        val card = fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>().active.single()
        card.percent shouldBe 75
        card.status shouldBe BudgetStatus.OK
    }

    "OnArchiveClick archives the budget and offers an undo" {
        val fixture = fixture()
        fixture.budgets.emit(
            listOf(aBudget(id = budgetId("b-1"), workspaceId = workspace, name = "Groceries", startDate = today())),
        )

        fixture.snackbar.requests.test {
            fixture.viewModel.onEvent(BudgetsEvent.OnArchiveClick(budgetId("b-1")))
            val request = awaitItem()
            request.message shouldBe Res.string.budgets_archived_snackbar
            request.messageArgs shouldBe listOf("Groceries")
            request.actionLabel shouldBe Res.string.budgets_archive_undo
        }

        fixture.budgets.snapshot().single().isActive shouldBe false
        val content = fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>()
        content.active shouldBe emptyList()
        content.archived.single().name shouldBe "Groceries"
    }

    "OnDeleteClick stages a confirmation and OnDeleteConfirm removes the budget" {
        val fixture = fixture()
        fixture.budgets.emit(
            listOf(aBudget(id = budgetId("b-1"), workspaceId = workspace, name = "Groceries", startDate = today())),
        )

        fixture.viewModel.onEvent(BudgetsEvent.OnDeleteClick(budgetId("b-1")))
        fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>()
            .pendingDelete?.name shouldBe "Groceries"

        fixture.viewModel.onEvent(BudgetsEvent.OnDeleteConfirm)

        fixture.budgets.deleted shouldBe listOf(budgetId("b-1"))
        fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>().active shouldBe emptyList()
    }

    "OnDeleteCancel keeps the budget" {
        val fixture = fixture()
        fixture.budgets.emit(
            listOf(aBudget(id = budgetId("b-1"), workspaceId = workspace, startDate = today())),
        )

        fixture.viewModel.onEvent(BudgetsEvent.OnDeleteClick(budgetId("b-1")))
        fixture.viewModel.onEvent(BudgetsEvent.OnDeleteCancel)

        fixture.viewModel.value.shouldBeInstanceOf<BudgetsState.Content>().pendingDelete shouldBe null
        fixture.budgets.deleted shouldBe emptyList()
    }
})

private class BudgetsFixture(
    val budgets: FakeBudgetRepository,
    val snackbar: SnackbarController,
    val viewModel: BudgetsViewModel,
)

private fun fixture(
    transactions: List<com.georgeci.moneysurfer.domain.model.Transaction> = emptyList(),
): BudgetsFixture {
    val budgetRepository = FakeBudgetRepository()
    val transactionRepository = FakeTransactionRepository(transactions)
    val workspaceRepository = FakeWorkspaceRepository(listOf(aWorkspace(id = workspace, baseCurrency = USD)))
    val categoryRepository = FakeCategoryRepository()
    val session = InMemorySessionPointers(currentWorkspaceId = workspace)
    val snackbar = SnackbarController()
    val clock = ClockUseCase()
    return BudgetsFixture(
        budgets = budgetRepository,
        snackbar = snackbar,
        viewModel = BudgetsViewModel(
            getBudgets = GetBudgetsUseCase(budgetRepository, session),
            getBudgetProgress = GetBudgetProgressUseCase(transactionRepository, workspaceRepository, clock),
            getCategories = GetCategoriesUseCase(categoryRepository, session),
            archiveBudget = ArchiveBudgetUseCase(budgetRepository),
            deleteBudget = DeleteBudgetUseCase(budgetRepository),
            session = session,
            snackbar = snackbar,
        ),
    )
}
