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
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_delete_undo
import moneysurfer.feature.category.generated.resources.categories_manage_deleted_snackbar

/**
 * Delete + Undo flow for `CategoriesManageViewModel`. The VM streams categories from a fake
 * repository, so a delete and a subsequent undo are both observable in `Content` state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesManageViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

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
        content.categories.shouldBeEmpty()
        repo.snapshot().shouldBeEmpty()
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
})

private fun newViewModel(
    repo: FakeCategoryRepository,
    workspaceId: WorkspaceId,
    snackbar: SnackbarController = SnackbarController(),
): CategoriesManageViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    return CategoriesManageViewModel(
        getCategories = GetCategoriesUseCase(repo, session),
        deleteCategory = DeleteCategoryUseCase(repo),
        categoryRepository = repo,
        snackbar = snackbar,
    )
}

private fun List<*>.shouldBeEmpty() {
    if (isNotEmpty()) error("expected empty list, got $this")
}

private class FakeCategoryRepository(initial: List<Category>) : CategoryRepository {
    private val state = MutableStateFlow(initial)

    fun snapshot(): List<Category> = state.value

    override fun getAll(): Flow<List<Category>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = state
    override suspend fun getById(id: CategoryId): Category? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) {
        state.value = state.value + category
    }
    override suspend fun update(category: Category) {
        state.value = state.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun delete(id: CategoryId) {
        state.value = state.value.filterNot { it.id == id }
    }
}
