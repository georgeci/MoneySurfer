package com.georgeci.moneysurfer.feature.category.manage

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_delete_undo
import moneysurfer.feature.category.generated.resources.categories_manage_deleted_snackbar

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesManageViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "starts in Loading before categories are emitted" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)

        viewModel.value shouldBe CategoriesManageState.Loading
    }

    "transitions to Content when categories arrive" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)

        repo.emit(
            listOf(
                aCategory(id = categoryId("c-1"), name = "Food"),
                aCategory(id = categoryId("c-2"), name = "Salary"),
            ),
        )

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.categories.map { it.name } shouldBe listOf("Food", "Salary")
    }

    "transitions to empty Content when categories are removed" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)

        repo.emit(listOf(aCategory(id = categoryId("c-1"), name = "Food")))
        repo.emit(emptyList())

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.categories shouldBe emptyList()
    }

    "OnRemoveCategoryClick stages a pending delete" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(listOf(aCategory(id = categoryId("c-1"), name = "Food")))

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(categoryId("c-1")))

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.pendingDelete?.id shouldBe categoryId("c-1")
        content.pendingDelete?.name shouldBe "Food"
    }

    "OnDeleteCancel clears pending without deleting" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(listOf(aCategory(id = categoryId("c-1"), name = "Food")))

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(categoryId("c-1")))
        viewModel.onEvent(CategoriesManageEvent.OnDeleteCancel)

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.pendingDelete shouldBe null
        repo.deleted shouldBe emptyList()
    }

    "OnDeleteConfirm removes the category and shows an undo snackbar" {
        val ws = workspaceId("ws-1")
        val category = aCategory(id = categoryId("c-1"), workspaceId = ws, name = "Food")
        val repo = FakeCategoryRepository(initial = listOf(category))
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(category.id))

        snackbar.requests.test {
            viewModel.onEvent(CategoriesManageEvent.OnDeleteConfirm)
            val request = awaitItem()
            request.message shouldBe Res.string.categories_manage_deleted_snackbar
            request.messageArgs shouldBe listOf("Food")
            request.actionLabel shouldBe Res.string.categories_manage_delete_undo
        }

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.pendingDelete shouldBe null
        content.categories.shouldBeEmpty()
        repo.snapshot().shouldBeEmpty()
        repo.deleted shouldBe listOf(category.id)
    }

    "tapping Undo on the delete snackbar restores the category" {
        val ws = workspaceId("ws-1")
        val category = aCategory(id = categoryId("c-1"), workspaceId = ws, name = "Food")
        val repo = FakeCategoryRepository(initial = listOf(category))
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(category.id))

        var onUndo: (suspend () -> Unit)? = null
        snackbar.requests.test {
            viewModel.onEvent(CategoriesManageEvent.OnDeleteConfirm)
            onUndo = awaitItem().onAction
        }
        repo.snapshot().shouldBeEmpty()

        onUndo.shouldNotBeNull().invoke()

        repo.snapshot().single().id shouldBe category.id
        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.categories.single().id shouldBe category.id
    }

    // ── tree rendering and parent deletes (issue #247) ────────────────────────────────────

    "children are listed directly under their parent, one level deep" {
        val parent = aCategory(id = categoryId("c-parent"), name = "Food")
        val child = aCategory(id = categoryId("c-child"), name = "Groceries", parentId = parent.id)
        val unrelated = aCategory(id = categoryId("c-other"), name = "Transport")
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)

        // Deliberately out of tree order — flattening, not input order, is what places the row.
        repo.emit(listOf(child, unrelated, parent))

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.categories.map { it.name to it.depth } shouldBe listOf(
            "Transport" to 0,
            "Food" to 0,
            "Groceries" to 1,
        )
    }

    "staging a delete counts the children the confirmation has to warn about" {
        val parent = aCategory(id = categoryId("c-parent"), name = "Food")
        val child = aCategory(id = categoryId("c-child"), name = "Groceries", parentId = parent.id)
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(listOf(parent, child))

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(parent.id))

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.pendingDelete?.childCount shouldBe 1
    }

    "deleting a parent keeps its children, moved up to the root" {
        val ws = workspaceId("ws-1")
        val parent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
        val child = aCategory(
            id = categoryId("c-child"),
            workspaceId = ws,
            name = "Groceries",
            parentId = parent.id,
        )
        val repo = FakeCategoryRepository(initial = listOf(parent, child))
        val viewModel = newViewModel(repo, ws)

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(parent.id))
        viewModel.onEvent(CategoriesManageEvent.OnDeleteConfirm)

        repo.snapshot().map { it.id to it.parentId } shouldBe listOf(child.id to null)
    }

    "undoing a parent delete re-attaches the children it had" {
        val ws = workspaceId("ws-1")
        val parent = aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Food")
        val child = aCategory(
            id = categoryId("c-child"),
            workspaceId = ws,
            name = "Groceries",
            parentId = parent.id,
        )
        val repo = FakeCategoryRepository(initial = listOf(parent, child))
        val snackbar = SnackbarController()
        val viewModel = newViewModel(repo, ws, snackbar)

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(parent.id))

        var onUndo: (suspend () -> Unit)? = null
        snackbar.requests.test {
            viewModel.onEvent(CategoriesManageEvent.OnDeleteConfirm)
            onUndo = awaitItem().onAction
        }

        onUndo.shouldNotBeNull().invoke()

        repo.snapshot().first { it.id == child.id }.parentId shouldBe parent.id
        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.categories.map { it.name to it.depth } shouldBe listOf("Food" to 0, "Groceries" to 1)
    }
})

private fun newViewModel(
    repo: FakeCategoryRepository,
    workspaceId: WorkspaceId = workspaceId("ws-1"),
    snackbar: SnackbarController = SnackbarController(),
): CategoriesManageViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    return CategoriesManageViewModel(
        getCategories = GetCategoriesUseCase(repo, session),
        deleteCategory = DeleteCategoryUseCase(repo),
        createCategory = CreateCategoryUseCase(repo),
        editCategory = EditCategoryUseCase(repo),
        snackbar = snackbar,
    )
}

private fun List<*>.shouldBeEmpty() {
    if (isNotEmpty()) error("expected empty list, got $this")
}

/**
 * Backed by a replay-1 [MutableSharedFlow] so a fresh fake (no `initial`) leaves the VM in
 * `Loading` until [emit] is called; passing `initial` seeds the stream up front. Writes
 * re-emit the mutated list so the VM observes deletes and Undo re-inserts.
 */
private class FakeCategoryRepository(initial: List<Category>? = null) : CategoryRepository {
    // Extra buffer beyond the replay slot: deleting a parent writes more than once (reparent
    // each child, then delete), and a zero-capacity replay flow refuses the second tryEmit
    // while a collector is still catching up.
    private val flow = MutableSharedFlow<List<Category>>(replay = 1, extraBufferCapacity = 16)
    private var current: List<Category> = initial ?: emptyList()
    val deleted = mutableListOf<CategoryId>()

    init {
        if (initial != null) check(flow.tryEmit(current)) { "failed to seed categories" }
    }

    fun emit(categories: List<Category>) {
        current = categories
        check(flow.tryEmit(categories)) { "failed to emit categories" }
    }

    fun snapshot(): List<Category> = current

    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = current.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) = emit(current + category)
    override suspend fun update(category: Category) =
        emit(current.map { if (it.id == category.id) category else it })

    override suspend fun delete(id: CategoryId) {
        deleted += id
        emit(current.filterNot { it.id == id })
    }
}
