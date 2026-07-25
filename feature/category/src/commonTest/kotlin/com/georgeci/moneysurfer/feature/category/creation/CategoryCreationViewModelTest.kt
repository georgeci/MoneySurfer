package com.georgeci.moneysurfer.feature.category.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.CategoryCapUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_created_snackbar

/**
 * CategoryCreationViewModel save flow. The reducer's required-field guard (`name.isBlank()`) is the
 * validator under test here — the screen has no standalone validator object, so the rule lives on
 * the ViewModel/state and is exercised through the real `CreateCategoryUseCase` against an
 * in-memory repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryCreationViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "a blank name is rejected — save is a no-op and canSave is false" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("   "))

                vm.currentState.canSave shouldBe false

                vm.onEvent(CategoryCreationEvent.OnSaveClick)
                fixture.categoryRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a non-blank name enables save" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.currentState.canSave shouldBe false

                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.currentState.canSave shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the name required error appears only after the field was touched and left blank" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.currentState.nameMissing shouldBe false

                vm.onEvent(CategoryCreationEvent.OnNameChanged("Food"))
                vm.currentState.nameMissing shouldBe false

                vm.onEvent(CategoryCreationEvent.OnNameChanged(""))
                vm.currentState.nameMissing shouldBe true
                vm.currentState.canSave shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "name input is truncated at the max length" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(
                    CategoryCreationEvent.OnNameChanged("x".repeat(CategoryCreationState.NAME_MAX_LENGTH + 10)),
                )

                vm.currentState.name.length shouldBe CategoryCreationState.NAME_MAX_LENGTH
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the character counter appears once the name reaches the threshold" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(
                    CategoryCreationEvent.OnNameChanged("x".repeat(CategoryCreationState.NAME_COUNTER_THRESHOLD - 1)),
                )
                vm.currentState.showNameCounter shouldBe false

                vm.onEvent(
                    CategoryCreationEvent.OnNameChanged("x".repeat(CategoryCreationState.NAME_COUNTER_THRESHOLD)),
                )
                vm.currentState.showNameCounter shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save creates an expense category in the current workspace and trims the name" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("  Groceries  "))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                val saved = fixture.categoryRepository.inserted.single()
                saved.name shouldBe "Groceries"
                saved.type shouldBe CategoryType.EXPENSE
                saved.workspaceId shouldBe ws
                saved.parentId shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the income toggle is reflected in the saved category type" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Salary"))
                vm.onEvent(CategoryCreationEvent.OnTypeChanged(CategoryTypeUi.Income))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.categoryRepository.inserted.single().type shouldBe CategoryType.INCOME
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save shows a created snackbar carrying the category name" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                fixture.snackbar.requests.test {
                    vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                    vm.onEvent(CategoryCreationEvent.OnSaveClick)
                    val request = awaitItem()
                    request.message shouldBe Res.string.category_creation_created_snackbar
                    request.messageArgs shouldBe listOf("Groceries")
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save emits NavigateBack" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.sideEffects.effectFlow.test {
                    vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                    vm.onEvent(CategoryCreationEvent.OnSaveClick)
                    awaitItem().shouldBeInstanceOf<CategoryCreationEffect.NavigateBack>()
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    // ── icon / colour / parent round-trip (issue #247) ────────────────────────────────────
    // The screen used to collect all three and save none of them.

    "the picked icon, colour and parent are persisted" {
        runTest {
            val parent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
            val fixture = Fixture(workspaceId = ws, existing = listOf(parent))
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnIconSelected(CategoryAppearance.ICON_KEYS[3]))
                vm.onEvent(CategoryCreationEvent.OnColorSelected(CategoryAppearance.HUES[5]))
                vm.onEvent(CategoryCreationEvent.OnParentSelected(parent.id))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                val saved = fixture.categoryRepository.inserted.single()
                saved.iconKey shouldBe CategoryAppearance.ICON_KEYS[3]
                saved.hue shouldBe CategoryAppearance.HUES[5]
                saved.parentId shouldBe parent.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "opening an existing category restores its icon, colour and parent" {
        runTest {
            val parent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
            val child = aCategory(
                id = categoryId("c-child"),
                workspaceId = ws,
                name = "Groceries",
                parentId = parent.id,
            ).copy(iconKey = CategoryAppearance.ICON_KEYS[6], hue = CategoryAppearance.HUES[2])
            val fixture = Fixture(workspaceId = ws, existing = listOf(parent, child))
            val vm = fixture.createViewModel(editing = child.id)
            try {
                vm.currentState.iconKey shouldBe CategoryAppearance.ICON_KEYS[6]
                vm.currentState.hue shouldBe CategoryAppearance.HUES[2]
                vm.currentState.parentId shouldBe parent.id
                vm.currentState.parentOptions.map { it.name } shouldContain "Food"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "editing saves the changed icon, colour and parent back onto the existing category" {
        runTest {
            val parent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
            val child = aCategory(id = categoryId("c-child"), workspaceId = ws, name = "Groceries")
            val fixture = Fixture(workspaceId = ws, existing = listOf(parent, child))
            val vm = fixture.createViewModel(editing = child.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnIconSelected(CategoryAppearance.ICON_KEYS[1]))
                vm.onEvent(CategoryCreationEvent.OnColorSelected(CategoryAppearance.HUES[4]))
                vm.onEvent(CategoryCreationEvent.OnParentSelected(parent.id))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                val updated = fixture.categoryRepository.getById(child.id).shouldNotBeNull()
                updated.iconKey shouldBe CategoryAppearance.ICON_KEYS[1]
                updated.hue shouldBe CategoryAppearance.HUES[4]
                updated.parentId shouldBe parent.id
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the parent picker offers neither the category itself nor its descendants" {
        runTest {
            val root = aCategory(id = categoryId("c-root"), workspaceId = ws, name = "Food")
            val child = aCategory(
                id = categoryId("c-child"),
                workspaceId = ws,
                name = "Groceries",
                parentId = root.id,
            )
            val other = aCategory(id = categoryId("c-other"), workspaceId = ws, name = "Transport")
            val fixture = Fixture(workspaceId = ws, existing = listOf(root, child, other))
            val vm = fixture.createViewModel(editing = root.id)
            try {
                vm.currentState.parentOptions.map { it.id } shouldBe listOf(other.id)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "switching the type drops a parent that belongs to the other type" {
        runTest {
            val expenseParent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
            val fixture = Fixture(workspaceId = ws, existing = listOf(expenseParent))
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnParentSelected(expenseParent.id))
                vm.currentState.parentId shouldBe expenseParent.id

                vm.onEvent(CategoryCreationEvent.OnTypeChanged(CategoryTypeUi.Income))

                vm.currentState.parentId shouldBe null
                vm.currentState.parentOptions.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    // ── monthly cap, backed by a single-category budget (issue #248) ──────────────────────
    // `Category` has no `cap` field: the control creates, updates and clears a budget whose only
    // category is this one.

    "a cap typed on a new category creates a monthly budget naming only that category" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnCapChanged("250"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                val saved = fixture.categoryRepository.inserted.single()
                val budget = fixture.budgetRepository.snapshot().single()
                budget.categoryIds shouldBe listOf(saved.id)
                budget.amount shouldBe 250.dollars
                budget.period shouldBe BudgetPeriod.MONTHLY
                budget.name shouldBe "Groceries"
                budget.workspaceId shouldBe ws
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "leaving the cap empty creates no budget at all" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "opening a category with a single-category budget prefills the cap field" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, categoryIds = listOf(category.id), amount = 300.dollars)
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.currentState.cap shouldBe "300"
                vm.currentState.capManagedByBudgetName.shouldBeNull()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "changing the cap updates the backing budget instead of creating a second one" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(
                id = budgetId("b-food"),
                workspaceId = ws,
                name = "Food",
                categoryIds = listOf(category.id),
                amount = 300.dollars,
            )
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnCapChanged("450"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                val budget = fixture.budgetRepository.snapshot().single()
                budget.id shouldBe backing.id
                budget.amount shouldBe 450.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a cap with cents survives the round-trip through the field" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(
                id = budgetId("b-food"),
                workspaceId = ws,
                categoryIds = listOf(category.id),
                amount = Money.fromMajor(12, 50),
            )
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                // Not "12.5" and not "1250" — the field speaks major units with both decimals.
                vm.currentState.cap shouldBe "12.50"

                vm.onEvent(CategoryCreationEvent.OnCapChanged("7.05"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().single().amount shouldBe Money.fromMajor(7, 5)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    // Zero is not a limit of zero — it means "no cap". Creating a budget for it would open at
    // 100% over budget the moment the first transaction lands.
    "a cap of zero creates no budget" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnCapChanged("0"))
                vm.currentState.capAsMoney.shouldBeNull()

                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a cap zeroed out on an existing category deletes the backing budget" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, categoryIds = listOf(category.id), amount = 300.dollars)
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnCapChanged("0"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a budget change arriving while the user types does not overwrite the cap field" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(
                id = budgetId("b-food"),
                workspaceId = ws,
                categoryIds = listOf(category.id),
                amount = 300.dollars,
            )
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.currentState.cap shouldBe "300"
                vm.onEvent(CategoryCreationEvent.OnCapChanged("450"))

                // A sync pull moves the same budget underneath the open form.
                fixture.budgetRepository.update(backing.copy(amount = 700.dollars))

                vm.currentState.cap shouldBe "450"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "clearing the cap deletes the backing budget" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, categoryIds = listOf(category.id), amount = 300.dollars)
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnCapChanged(""))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "renaming the category carries an untouched backing budget's name along" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, name = "Food", categoryIds = listOf(category.id))
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().single().name shouldBe "Groceries"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a budget the user renamed on the Budgets screen keeps its own name" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, name = "Eating out", categoryIds = listOf(category.id))
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot().single().name shouldBe "Eating out"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a multi-category budget takes the control read-only and names the budget" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val shared = aBudget(
                workspaceId = ws,
                name = "Living",
                categoryIds = listOf(category.id, categoryId("c-rent")),
                amount = 900.dollars,
            )
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(shared))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.currentState.capManagedByBudgetName shouldBe "Living"
                // The shared limit is never mirrored into the field the shortcut would write back.
                vm.currentState.cap shouldBe ""
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "saving under a multi-category budget adds no second overlapping limit" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val shared = aBudget(
                workspaceId = ws,
                name = "Living",
                categoryIds = listOf(category.id, categoryId("c-rent")),
                amount = 900.dollars,
            )
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(shared))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                // A stale field value must not leak through the read-only control.
                vm.onEvent(CategoryCreationEvent.OnCapChanged("120"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.budgetRepository.snapshot() shouldBe listOf(shared)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the cap control is hidden for income categories and a switch to income drops the budget" {
        runTest {
            val category = aCategory(id = categoryId("c-food"), workspaceId = ws, name = "Food")
            val backing = aBudget(workspaceId = ws, categoryIds = listOf(category.id))
            val fixture = Fixture(workspaceId = ws, existing = listOf(category), budgets = listOf(backing))
            val vm = fixture.createViewModel(editing = category.id)
            try {
                vm.currentState.showCap shouldBe true

                vm.onEvent(CategoryCreationEvent.OnTypeChanged(CategoryTypeUi.Income))
                vm.currentState.showCap shouldBe false

                vm.onEvent(CategoryCreationEvent.OnSaveClick)
                fixture.budgetRepository.snapshot().shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save is a no-op when no workspace is pinned" {
        runTest {
            val fixture = Fixture(workspaceId = null)
            val vm = fixture.createViewModel()
            try {
                vm.onEvent(CategoryCreationEvent.OnNameChanged("Groceries"))
                vm.onEvent(CategoryCreationEvent.OnSaveClick)

                fixture.categoryRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private fun List<*>.shouldBeEmpty() {
    if (isNotEmpty()) error("Expected empty list, got $this")
}

private class Fixture(
    workspaceId: WorkspaceId?,
    existing: List<Category> = emptyList(),
    budgets: List<Budget> = emptyList(),
) {
    val categoryRepository = FakeCategoryRepository(existing)
    val budgetRepository = FakeBudgetRepository(budgets)
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val snackbar = SnackbarController()
    private val clock = ClockUseCase()

    fun createViewModel(editing: CategoryId? = null) = CategoryCreationViewModel(
        categoryId = editing,
        createCategory = CreateCategoryUseCase(categoryRepository),
        editCategory = EditCategoryUseCase(categoryRepository),
        categoryRepository = categoryRepository,
        getCategories = GetCategoriesUseCase(categoryRepository, session),
        categoryCap = CategoryCapUseCase(
            budgetRepository = budgetRepository,
            getBudgets = GetBudgetsUseCase(budgetRepository, session),
            clock = clock,
        ),
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(clock),
        snackbar = snackbar,
    )
}

private class FakeCategoryRepository(existing: List<Category> = emptyList()) : CategoryRepository {
    val inserted = mutableListOf<Category>()
    private val byId = existing.associateBy { it.id }.toMutableMap()
    private val all = MutableStateFlow(existing)

    override fun getAll(): Flow<List<Category>> = all
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = all
    override suspend fun getById(id: CategoryId): Category? = byId[id]
    override suspend fun insert(category: Category) {
        inserted += category
        byId[category.id] = category
        all.value = byId.values.toList()
    }
    override suspend fun update(category: Category) {
        byId[category.id] = category
        all.value = byId.values.toList()
    }
    override suspend fun delete(id: CategoryId) {
        byId.remove(id)
        all.value = byId.values.toList()
    }
}

private class FakeBudgetRepository(existing: List<Budget> = emptyList()) : BudgetRepository {
    private val byId = existing.associateBy { it.id }.toMutableMap()
    private val all = MutableStateFlow(existing)

    fun snapshot(): List<Budget> = all.value

    override fun getAll(): Flow<List<Budget>> = all
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = all
    override suspend fun getById(id: BudgetId): Budget? = byId[id]
    override suspend fun insert(budget: Budget) = publish { byId[budget.id] = budget }
    override suspend fun update(budget: Budget) = publish { byId[budget.id] = budget }
    override suspend fun setActive(id: BudgetId, isActive: Boolean) = publish {
        byId[id]?.let { byId[id] = it.copy(isActive = isActive) }
    }

    override suspend fun delete(id: BudgetId) = publish { byId.remove(id) }

    private inline fun publish(mutate: () -> Unit) {
        mutate()
        all.value = byId.values.toList()
    }
}
