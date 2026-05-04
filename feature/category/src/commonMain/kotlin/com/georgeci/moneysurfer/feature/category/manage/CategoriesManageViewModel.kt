package com.georgeci.moneysurfer.feature.category.manage

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CategoriesManageViewModel(
    private val getCategories: GetCategoriesUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : MviViewModel<CategoriesManageState, CategoriesManageEvent, CategoriesManageEffect>(
    initialState = CategoriesManageState.Loading,
) {

    init {
        observeCategories()
    }

    override fun onEvent(event: CategoriesManageEvent) {
        when (event) {
            CategoriesManageEvent.OnBackClick -> postSideEffect(CategoriesManageEffect.NavigateBack)
            CategoriesManageEvent.OnAddCategoryClick -> postSideEffect(
                CategoriesManageEffect.NavigateToCategoryCreation,
            )
            is CategoriesManageEvent.OnCategoryClick ->
                postSideEffect(CategoriesManageEffect.NavigateToCategoryEdit(event.categoryId))
            is CategoriesManageEvent.OnRemoveCategoryClick -> requestDelete(event.categoryId)
            CategoriesManageEvent.OnDeleteCancel -> dismissDelete()
            CategoriesManageEvent.OnDeleteConfirm -> confirmDelete()
        }
    }

    private fun requestDelete(categoryId: CategoryId) {
        val content = currentState as? CategoriesManageState.Content ?: return
        val target = content.categories.firstOrNull { it.id == categoryId } ?: return
        updateState {
            CategoriesManageState.content.pendingDelete.modify(this) {
                CategoriesManagePendingDelete(id = target.id, name = target.name)
            }
        }
    }

    private fun dismissDelete() = updateState {
        CategoriesManageState.content.pendingDelete.modify(this) { null }
    }

    private fun confirmDelete() {
        val content = currentState as? CategoriesManageState.Content ?: return
        val target = content.pendingDelete ?: return
        updateState { CategoriesManageState.content.pendingDelete.modify(this) { null } }
        launch { deleteCategory(target.id) }
    }

    private fun observeCategories() {
        launch {
            getCategories().collect { categories ->
                updateState {
                    when (this) {
                        is CategoriesManageState.Loading -> CategoriesManageState.Content(
                            categories = categories.map { it.toUi() },
                        )
                        is CategoriesManageState.Content -> copy(
                            categories = categories.map { it.toUi() },
                        )
                    }
                }
            }
        }
    }

    private fun Category.toUi() = CategoryManageUi(
        id = id,
        name = name,
        type = type,
    )
}

@optics
sealed interface CategoriesManageState {
    data object Loading : CategoriesManageState

    @optics
    data class Content(
        val categories: List<CategoryManageUi>,
        val pendingDelete: CategoriesManagePendingDelete? = null,
    ) : CategoriesManageState {
        companion object
    }

    companion object
}

data class CategoriesManagePendingDelete(
    val id: CategoryId,
    val name: String,
)

data class CategoryManageUi(
    val id: CategoryId,
    val name: String,
    val type: CategoryType,
)

sealed interface CategoriesManageEvent {
    data object OnBackClick : CategoriesManageEvent
    data object OnAddCategoryClick : CategoriesManageEvent
    data class OnCategoryClick(val categoryId: CategoryId) : CategoriesManageEvent
    data class OnRemoveCategoryClick(val categoryId: CategoryId) : CategoriesManageEvent
    data object OnDeleteConfirm : CategoriesManageEvent
    data object OnDeleteCancel : CategoriesManageEvent
}

sealed interface CategoriesManageEffect {
    data object NavigateBack : CategoriesManageEffect
    data object NavigateToCategoryCreation : CategoriesManageEffect
    data class NavigateToCategoryEdit(val categoryId: CategoryId) : CategoriesManageEffect
}
