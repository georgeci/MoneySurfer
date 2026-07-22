package com.georgeci.moneysurfer.feature.category.manage

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.CategoryNode
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_delete_undo
import moneysurfer.feature.category.generated.resources.categories_manage_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CategoriesManageViewModel(
    private val getCategories: GetCategoriesUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val editCategory: EditCategoryUseCase,
    private val snackbar: SnackbarController,
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
                CategoriesManagePendingDelete(
                    id = target.id,
                    name = target.name,
                    // Drives the extra line in the dialog: children survive the delete, they
                    // just move up to the root, and the user should know that before tapping.
                    childCount = content.categories.count { it.parentId == target.id },
                )
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
        launch {
            val deletion = deleteCategory(target.id) ?: return@launch
            snackbar.show(
                message = Res.string.categories_manage_deleted_snackbar,
                messageArgs = listOf(deletion.category.name),
                actionLabel = Res.string.categories_manage_delete_undo,
                onAction = {
                    // Restore the parent first — the children carry a foreign key back to it,
                    // so re-attaching them before it exists would be rejected.
                    createCategory(deletion.category)
                    deletion.reparentedChildren.forEach { editCategory(it) }
                },
            )
        }
    }

    private fun observeCategories() {
        launch {
            getCategories().collect { categories ->
                val rows = CategoryTree.flatten(categories).map { it.toUi() }
                updateState {
                    when (this) {
                        is CategoriesManageState.Loading -> CategoriesManageState.Content(categories = rows)
                        is CategoriesManageState.Content -> copy(categories = rows)
                    }
                }
            }
        }
    }

    private fun CategoryNode.toUi() = CategoryManageUi(
        id = category.id,
        name = category.name,
        type = category.type,
        parentId = category.parentId,
        iconKey = category.iconKey,
        hue = category.hue,
        systemKind = category.systemKind?.name,
        depth = depth,
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
    /** How many children will be moved up to the root if this delete goes through. */
    val childCount: Int = 0,
)

/** One row of the manage list, already placed in the tree by [CategoryTree.flatten]. */
data class CategoryManageUi(
    val id: CategoryId,
    val name: String,
    val type: CategoryType,
    val parentId: CategoryId? = null,
    val iconKey: String = "",
    val hue: Int = -1,
    val systemKind: String? = null,
    /** 0 for a root, 1 for a child. Drives the row's indent. */
    val depth: Int = 0,
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
