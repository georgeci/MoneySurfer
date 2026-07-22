package com.georgeci.moneysurfer.feature.category.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
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
                vm.currentState.selectedParentName shouldBe "Food"
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

private class Fixture(workspaceId: WorkspaceId?, existing: List<Category> = emptyList()) {
    val categoryRepository = FakeCategoryRepository(existing)
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val snackbar = SnackbarController()
    private val clock = ClockUseCase()

    fun createViewModel(editing: CategoryId? = null) = CategoryCreationViewModel(
        categoryId = editing,
        createCategory = CreateCategoryUseCase(categoryRepository),
        editCategory = EditCategoryUseCase(categoryRepository),
        categoryRepository = categoryRepository,
        getCategories = GetCategoriesUseCase(categoryRepository, session),
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
