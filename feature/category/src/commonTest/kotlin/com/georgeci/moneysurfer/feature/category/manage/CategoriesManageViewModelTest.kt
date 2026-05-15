package com.georgeci.moneysurfer.feature.category.manage

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

    "OnDeleteConfirm clears pending and deletes the category" {
        val repo = FakeCategoryRepository()
        val viewModel = newViewModel(repo)
        repo.emit(listOf(aCategory(id = categoryId("c-1"), name = "Food")))

        viewModel.onEvent(CategoriesManageEvent.OnRemoveCategoryClick(categoryId("c-1")))
        viewModel.onEvent(CategoriesManageEvent.OnDeleteConfirm)

        val content = viewModel.value.shouldBeInstanceOf<CategoriesManageState.Content>()
        content.pendingDelete shouldBe null
        repo.deleted shouldBe listOf(categoryId("c-1"))
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
})

private fun newViewModel(repo: FakeCategoryRepository): CategoriesManageViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId("ws-1"))
    return CategoriesManageViewModel(
        getCategories = GetCategoriesUseCase(repo, session),
        deleteCategory = DeleteCategoryUseCase(repo),
    )
}

private class FakeCategoryRepository : CategoryRepository {
    private val flow = MutableSharedFlow<List<Category>>(replay = 1)
    val deleted = mutableListOf<CategoryId>()

    fun emit(categories: List<Category>) {
        check(flow.tryEmit(categories)) { "failed to emit categories" }
    }

    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = null
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) {
        deleted += id
    }
}
