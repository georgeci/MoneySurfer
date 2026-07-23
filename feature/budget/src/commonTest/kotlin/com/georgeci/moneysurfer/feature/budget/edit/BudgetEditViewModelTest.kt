package com.georgeci.moneysurfer.feature.budget.edit

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.EditBudgetUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.feature.budget.FakeBudgetRepository
import com.georgeci.moneysurfer.feature.budget.FakeCategoryRepository
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

private val workspace = workspaceId("ws-1")

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEditViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "save is blocked until a name and a positive limit are entered" {
        val fixture = fixture()

        fixture.viewModel.value.canSave shouldBe false

        fixture.viewModel.onEvent(BudgetEditEvent.OnNameChanged("Groceries"))
        fixture.viewModel.value.canSave shouldBe false

        fixture.viewModel.onEvent(BudgetEditEvent.OnAmountChanged("0"))
        fixture.viewModel.value.canSave shouldBe false

        fixture.viewModel.onEvent(BudgetEditEvent.OnAmountChanged("400"))
        fixture.viewModel.value.canSave shouldBe true
    }

    "saving a new budget writes the entered values" {
        val fixture = fixture()

        fixture.viewModel.onEvent(BudgetEditEvent.OnNameChanged("Groceries"))
        fixture.viewModel.onEvent(BudgetEditEvent.OnAmountChanged("400.50"))
        fixture.viewModel.onEvent(BudgetEditEvent.OnPeriodChanged(BudgetPeriod.WEEKLY))
        fixture.viewModel.onEvent(BudgetEditEvent.OnAlertPercentChanged(75))
        fixture.viewModel.onEvent(BudgetEditEvent.OnRolloverChanged(enabled = true))
        fixture.viewModel.onEvent(BudgetEditEvent.OnSaveClick)

        val saved = fixture.budgets.snapshot().single()
        saved.name shouldBe "Groceries"
        saved.amount shouldBe com.georgeci.moneysurfer.domain.primitives.Money(40_050)
        saved.period shouldBe BudgetPeriod.WEEKLY
        saved.alertPercent shouldBe 75
        saved.rollover shouldBe true
        saved.workspaceId shouldBe workspace
        saved.categoryIds shouldBe emptyList()
    }

    "only expense categories are offered" {
        val fixture = fixture(
            categories = listOf(
                aCategory(id = categoryId("c-1"), name = "Groceries", type = CategoryType.EXPENSE),
                aCategory(id = categoryId("c-2"), name = "Salary", type = CategoryType.INCOME),
            ),
        )

        fixture.viewModel.value.categoryOptions.map { it.name } shouldBe listOf("Groceries")
    }

    "picking categories narrows the budget, and All categories clears it again" {
        val fixture = fixture(
            categories = listOf(
                aCategory(id = categoryId("c-1"), name = "Groceries", type = CategoryType.EXPENSE),
            ),
        )

        fixture.viewModel.onEvent(BudgetEditEvent.OnCategoryToggled("c-1"))
        fixture.viewModel.value.selectedCategoryIds shouldBe setOf("c-1")

        fixture.viewModel.onEvent(BudgetEditEvent.OnAllCategoriesSelected)
        fixture.viewModel.value.selectedCategoryIds shouldBe emptySet()
    }

    "a selection whose category was deleted is dropped rather than saved as a dangling id" {
        val category = aCategory(id = categoryId("c-1"), name = "Groceries", type = CategoryType.EXPENSE)
        val fixture = fixture(categories = listOf(category))

        fixture.viewModel.onEvent(BudgetEditEvent.OnCategoryToggled("c-1"))
        fixture.categories.emit(emptyList())

        fixture.viewModel.value.selectedCategoryIds shouldBe emptySet()
    }

    "editing loads the existing budget and updates it in place" {
        val existing = aBudget(
            id = budgetId("b-1"),
            workspaceId = workspace,
            name = "Groceries",
            amount = 400.dollars,
            categoryIds = emptyList(),
            startDate = LocalDate(2026, 4, 1),
            alertPercent = 80,
        )
        val fixture = fixture(budgets = listOf(existing), budgetId = existing.id)

        fixture.viewModel.value.name shouldBe "Groceries"
        fixture.viewModel.value.amount shouldBe "400"
        fixture.viewModel.value.startDate shouldBe LocalDate(2026, 4, 1)

        fixture.viewModel.onEvent(BudgetEditEvent.OnNameChanged("Food"))
        fixture.viewModel.onEvent(BudgetEditEvent.OnSaveClick)

        val saved = fixture.budgets.snapshot().single()
        saved.id shouldBe existing.id
        saved.name shouldBe "Food"
        saved.createdAt shouldBe existing.createdAt
    }
})

private class BudgetEditFixture(
    val budgets: FakeBudgetRepository,
    val categories: FakeCategoryRepository,
    val viewModel: BudgetEditViewModel,
)

private fun fixture(
    budgets: List<com.georgeci.moneysurfer.domain.model.Budget> = emptyList(),
    categories: List<com.georgeci.moneysurfer.domain.model.Category> = emptyList(),
    budgetId: com.georgeci.moneysurfer.domain.primitives.BudgetId? = null,
): BudgetEditFixture {
    val budgetRepository = FakeBudgetRepository(budgets)
    val categoryRepository = FakeCategoryRepository(categories)
    val session = InMemorySessionPointers(currentWorkspaceId = workspace)
    val clock = ClockUseCase()
    return BudgetEditFixture(
        budgets = budgetRepository,
        categories = categoryRepository,
        viewModel = BudgetEditViewModel(
            budgetId = budgetId,
            budgetRepository = budgetRepository,
            createBudget = CreateBudgetUseCase(budgetRepository),
            editBudget = EditBudgetUseCase(budgetRepository),
            getCategories = GetCategoriesUseCase(categoryRepository, session),
            getCurrentTime = GetCurrentTimeUseCase(clock),
            session = session,
            clock = clock,
            snackbar = SnackbarController(),
        ),
    )
}
