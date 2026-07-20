package com.georgeci.moneysurfer.feature.category.creation

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_created_snackbar
import moneysurfer.feature.category.generated.resources.category_creation_updated_snackbar
import org.koin.core.annotation.KoinViewModel

/** Local UI model for the category type toggle. The domain `Category` model does not yet carry
 *  an income flag — this is kept as UI-only state for now. */
enum class CategoryTypeUi { Expense, Income }

@KoinViewModel
class CategoryCreationViewModel(
    private val categoryId: CategoryId?,
    private val createCategory: CreateCategoryUseCase,
    private val editCategory: EditCategoryUseCase,
    private val categoryRepository: CategoryRepository,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<CategoryCreationState, CategoryCreationEvent, CategoryCreationEffect>(
    initialState = CategoryCreationState(),
) {

    init {
        if (categoryId != null) loadCategory(categoryId)
    }

    override fun onEvent(event: CategoryCreationEvent) {
        when (event) {
            is CategoryCreationEvent.OnNameChanged -> updateState {
                copy(
                    name = event.name.take(CategoryCreationState.NAME_MAX_LENGTH),
                    nameTouched = true,
                )
            }
            is CategoryCreationEvent.OnTypeChanged -> updateState { copy(type = event.type) }
            is CategoryCreationEvent.OnIconSelected -> updateState { copy(selectedIconIndex = event.index) }
            is CategoryCreationEvent.OnColorSelected -> updateState { copy(selectedColorIndex = event.index) }
            is CategoryCreationEvent.OnMonthlyCapChanged -> updateState { copy(monthlyCap = event.value) }
            is CategoryCreationEvent.OnParentChanged -> updateState { copy(parent = event.value) }
            is CategoryCreationEvent.OnNoteChanged -> updateState { copy(note = event.value) }
            is CategoryCreationEvent.OnToggleExtraField -> toggleField(event.field, event.enabled)
            CategoryCreationEvent.OnCancelClick -> postSideEffect(CategoryCreationEffect.NavigateBack)
            CategoryCreationEvent.OnBackClick -> postSideEffect(CategoryCreationEffect.NavigateBack)
            CategoryCreationEvent.OnSaveClick -> saveCategory()
        }
    }

    private fun loadCategory(id: CategoryId) {
        launch {
            updateState { copy(isLoading = true) }
            val category = categoryRepository.getById(id)
            if (category != null) {
                updateState {
                    copy(
                        name = category.name,
                        type = if (category.type == CategoryType.INCOME) CategoryTypeUi.Income else CategoryTypeUi.Expense,
                        isLoading = false,
                    )
                }
            } else {
                updateState { copy(isLoading = false) }
            }
        }
    }

    private fun toggleField(field: CategoryCreationExtraField, enabled: Boolean) {
        updateState {
            when (field) {
                CategoryCreationExtraField.MonthlyCap -> copy(
                    showMonthlyCap = enabled,
                    monthlyCap = if (!enabled) "" else monthlyCap,
                )
                CategoryCreationExtraField.Parent -> copy(showParent = enabled, parent = if (!enabled) "" else parent)
                CategoryCreationExtraField.Note -> copy(showNote = enabled, note = if (!enabled) "" else note)
            }
        }
    }

    private fun saveCategory() {
        val state = currentState
        if (state.name.isBlank()) return
        launch {
            updateState { copy(isLoading = true) }

            val trimmedName = state.name.trim()
            if (categoryId != null) {
                val existing = categoryRepository.getById(categoryId)
                if (existing != null) {
                    editCategory(
                        existing.copy(
                            name = trimmedName,
                            type = if (state.type == CategoryTypeUi.Income) CategoryType.INCOME else CategoryType.EXPENSE,
                        ),
                    )
                    snackbar.show(Res.string.category_creation_updated_snackbar, listOf(trimmedName))
                }
                postSideEffect(CategoryCreationEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.flow.first()
            if (workspaceId == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            createCategory(
                Category(
                    id = CategoryId.uuid(),
                    workspaceId = workspaceId,
                    name = trimmedName,
                    type = if (state.type == CategoryTypeUi.Income) CategoryType.INCOME else CategoryType.EXPENSE,
                    parentId = null,
                    createdAt = getCurrentTime(),
                ),
            )
            snackbar.show(Res.string.category_creation_created_snackbar, listOf(trimmedName))
            postSideEffect(CategoryCreationEffect.NavigateBack)
        }
    }
}

enum class CategoryCreationExtraField { MonthlyCap, Parent, Note }

data class CategoryCreationState(
    val name: String = "",
    val type: CategoryTypeUi = CategoryTypeUi.Expense,
    val selectedIconIndex: Int = 0,
    val selectedColorIndex: Int = 0,
    val showMonthlyCap: Boolean = false,
    val showParent: Boolean = false,
    val showNote: Boolean = false,
    val monthlyCap: String = "",
    val parent: String = "",
    val note: String = "",
    val isLoading: Boolean = false,
    /** Whether the user has edited the name field — gates the required-name inline error
     *  so a pristine screen doesn't open covered in red. */
    val nameTouched: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isLoading

    /** Whether to show the required-name inline error: the field was edited and left blank. */
    val nameMissing: Boolean get() = nameTouched && name.isBlank()

    /** Whether the name is long enough that the character counter should appear. */
    val showNameCounter: Boolean get() = name.length >= NAME_COUNTER_THRESHOLD

    companion object {
        /** Hard cap on the category name — longer input is truncated in the reducer. */
        const val NAME_MAX_LENGTH = 50

        /** Name length at which the "n/max" character counter becomes visible. */
        const val NAME_COUNTER_THRESHOLD = 40
    }
}

sealed interface CategoryCreationEvent {
    data class OnNameChanged(val name: String) : CategoryCreationEvent
    data class OnTypeChanged(val type: CategoryTypeUi) : CategoryCreationEvent
    data class OnIconSelected(val index: Int) : CategoryCreationEvent
    data class OnColorSelected(val index: Int) : CategoryCreationEvent
    data class OnToggleExtraField(val field: CategoryCreationExtraField, val enabled: Boolean) : CategoryCreationEvent
    data class OnMonthlyCapChanged(val value: String) : CategoryCreationEvent
    data class OnParentChanged(val value: String) : CategoryCreationEvent
    data class OnNoteChanged(val value: String) : CategoryCreationEvent
    data object OnCancelClick : CategoryCreationEvent
    data object OnBackClick : CategoryCreationEvent
    data object OnSaveClick : CategoryCreationEvent
}

sealed interface CategoryCreationEffect {
    data object NavigateBack : CategoryCreationEffect
}
