package com.georgeci.moneysurfer.feature.category.picker

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryChooserViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "starts in Loading before categories arrive" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)

        viewModel.value.shouldBeInstanceOf<CategoryChooserState.Loading>()
    }

    // ── shared chrome ────────────────────────────────────────────────────────────────────

    "opens on the type of the currently selected category" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo, selectedId = categoryId("salary"))
        repo.emit(
            listOf(
                aCategory(id = categoryId("food"), name = "Food"),
                aCategory(id = categoryId("salary"), name = "Salary", type = CategoryType.INCOME),
            ),
        )

        val content = viewModel.content()
        content.type shouldBe CategoryType.INCOME
        content.selectedName shouldBe "Salary"
    }

    "a caller-pinned type locks the tab row" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo, filterType = CategoryType.TRANSFER)
        repo.emit(listOf(aCategory(id = categoryId("food"), name = "Food")))

        val content = viewModel.content()
        content.type shouldBe CategoryType.TRANSFER
        content.typeLocked shouldBe true
    }

    "the tab row switches both variants over to the picked type" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(
            listOf(
                aCategory(id = categoryId("food"), name = "Food"),
                aCategory(id = categoryId("salary"), name = "Salary", type = CategoryType.INCOME),
                aCategory(id = categoryId("move"), name = "Move", type = CategoryType.TRANSFER),
            ),
        )

        viewModel.onEvent(CategoryChooserEvent.OnTypeSelected(CategoryType.TRANSFER))

        val content = viewModel.content()
        content.gridItems.map { it.name } shouldBe listOf("Move")
        content.treeItems.map { it.parent.name } shouldBe listOf("Move")
    }

    "selecting a category publishes it back to the caller" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(listOf(aCategory(id = categoryId("food"), name = "Food")))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(CategoryChooserEvent.OnCategorySelected(categoryId("food")))
            awaitItem() shouldBe CategoryChooserEffect.PublishResult(categoryId("food"))
        }
    }

    "create and dismiss actions publish their host effects" {
        val viewModel = newViewModel(FakeCategoryRepository())

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(CategoryChooserEvent.OnCreateNewCategoryClick)
            awaitItem() shouldBe CategoryChooserEffect.NavigateToCategoryCreation
            viewModel.onEvent(CategoryChooserEvent.OnDismiss)
            awaitItem() shouldBe CategoryChooserEffect.Dismiss
        }
    }

    "content-editing events received while loading are harmless no-ops" {
        val viewModel = newViewModel(FakeCategoryRepository())

        viewModel.onEvent(CategoryChooserEvent.OnQueryChange("food"))
        viewModel.onEvent(CategoryChooserEvent.OnTypeSelected(CategoryType.INCOME))
        viewModel.onEvent(CategoryChooserEvent.OnParentExpandToggle(categoryId("food")))
        viewModel.onEvent(CategoryChooserEvent.OnVariantToggle)

        viewModel.value shouldBe CategoryChooserState.Loading(selectedId = null)
    }

    // ── variant A: grid ──────────────────────────────────────────────────────────────────

    "the grid flattens parents and children of the selected type into one list" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo, selectedId = categoryId("groceries"))
        repo.emit(sampleExpenses() + aCategory(id = categoryId("salary"), name = "Salary", type = CategoryType.INCOME))

        val grid = viewModel.content().gridItems
        grid.map { it.name } shouldBe listOf("Food", "Groceries", "Dining", "Transport")
        grid.single { it.selected }.name shouldBe "Groceries"
    }

    "search narrows the grid by name" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnQueryChange("gro"))

        viewModel.content().gridItems.map { it.name } shouldBe listOf("Groceries")
    }

    // ── variant B: tree ──────────────────────────────────────────────────────────────────

    "the tree groups children under their parent and counts them" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        val tree = viewModel.content().treeItems
        tree.map { it.parent.name to it.childCount } shouldBe listOf("Food" to 2, "Transport" to 0)
        // Nothing is selected, so nothing auto-expands.
        tree.all { it.children.isEmpty() } shouldBe true
    }

    "the parent holding the selection starts expanded" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo, selectedId = categoryId("dining"))
        repo.emit(sampleExpenses())

        val food = viewModel.content().treeItems.first { it.parent.name == "Food" }
        food.expanded shouldBe true
        food.children.single { it.selected }.name shouldBe "Dining"
    }

    "the chevron toggles one parent at a time" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnParentExpandToggle(categoryId("food")))
        viewModel.content().treeItems.first().children.map { it.name } shouldBe
            listOf("Groceries", "Dining")

        viewModel.onEvent(CategoryChooserEvent.OnParentExpandToggle(categoryId("food")))
        viewModel.content().treeItems.first().children shouldBe emptyList()
    }

    "search keeps a matching child visible under its parent" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnQueryChange("dining"))

        val tree = viewModel.content().treeItems
        tree.map { it.parent.name } shouldBe listOf("Food")
        tree.single().expanded shouldBe true
        tree.single().children.map { it.name } shouldBe listOf("Dining")
        // The subline still counts every child, not just the one the search left standing.
        tree.single().childCount shouldBe 2
    }

    "a parent that matches on its own name survives a search that no child matches" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnQueryChange("transp"))

        viewModel.content().treeItems.map { it.parent.name } shouldBe listOf("Transport")
    }

    "a child whose parent is a different type is drawn at the top level" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        val incomeParent = aCategory(
            id = categoryId("salary"),
            name = "Salary",
            type = CategoryType.INCOME,
        )
        repo.emit(
            listOf(
                incomeParent,
                aCategory(id = categoryId("bonus"), name = "Bonus", parentId = incomeParent.id),
            ),
        )

        // The child is an EXPENSE under an INCOME parent — a shape sync can deliver even though
        // the editor refuses to create it. Dropping it would hide a real category.
        viewModel.content().treeItems.map { it.parent.name } shouldBe listOf("Bonus")
    }

    "the header toggle flips between the two variants" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnVariantToggle)
        viewModel.content().variant shouldBe CategoryPickerVariant.TREE

        viewModel.onEvent(CategoryChooserEvent.OnVariantToggle)
        viewModel.content().variant shouldBe CategoryPickerVariant.GRID
    }

    "a re-emission from sync keeps the type, query and expansion the user chose" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(sampleExpenses())

        viewModel.onEvent(CategoryChooserEvent.OnParentExpandToggle(categoryId("food")))
        viewModel.onEvent(CategoryChooserEvent.OnQueryChange("o"))
        repo.emit(sampleExpenses() + aCategory(id = categoryId("fuel"), name = "Fuel"))

        val content = viewModel.content()
        content.query shouldBe "o"
        content.expandedIds shouldBe setOf(categoryId("food"))
        content.gridItems.map { it.name } shouldBe listOf("Food", "Groceries", "Transport")
    }

    "a re-emission resolves a previously stale selected category name" {
        val repo = FakeCategoryRepository()
        val selected = categoryId("late")
        val viewModel = newViewModel(repo, selectedId = selected)
        repo.emit(sampleExpenses())
        viewModel.content().selectedName shouldBe null

        repo.emit(sampleExpenses() + aCategory(id = selected, name = "Late arrival"))

        viewModel.content().selectedName shouldBe "Late arrival"
    }
})

private fun sampleExpenses(): List<Category> {
    val food = aCategory(id = categoryId("food"), name = "Food")
    return listOf(
        food,
        aCategory(id = categoryId("groceries"), name = "Groceries", parentId = food.id),
        aCategory(id = categoryId("dining"), name = "Dining", parentId = food.id),
        aCategory(id = categoryId("transport"), name = "Transport"),
    )
}

private fun CategoryChooserViewModel.content(): CategoryChooserState.Content =
    value.shouldBeInstanceOf<CategoryChooserState.Content>()

private fun newViewModel(
    repo: FakeCategoryRepository,
    selectedId: CategoryId? = null,
    filterType: CategoryType? = null,
    variant: CategoryPickerVariant = CategoryPickerVariant.GRID,
): CategoryChooserViewModel = CategoryChooserViewModel(
    initialSelectedId = selectedId,
    filterType = filterType,
    initialVariant = variant,
    getCategories = GetCategoriesUseCase(
        repo,
        InMemorySessionPointers(currentWorkspaceId = workspaceId("ws-1")),
    ),
)

/** Replay-1 so a fake with no [emit] yet leaves the ViewModel in `Loading`. */
private class FakeCategoryRepository : CategoryRepository {
    private val flow = MutableSharedFlow<List<Category>>(replay = 1, extraBufferCapacity = 8)
    private var current: List<Category> = emptyList()

    fun emit(categories: List<Category>) {
        current = categories
        check(flow.tryEmit(categories)) { "failed to emit categories" }
    }

    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = current.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) = emit(current + category)
    override suspend fun update(category: Category) =
        emit(current.map { if (it.id == category.id) category else it })

    override suspend fun delete(id: CategoryId) = emit(current.filterNot { it.id == id })
}
