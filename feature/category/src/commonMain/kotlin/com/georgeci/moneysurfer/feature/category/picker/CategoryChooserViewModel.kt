package com.georgeci.moneysurfer.feature.category.picker

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CategoryChooserViewModel(
    initialSelectedId: CategoryId?,
    private val filterType: CategoryType?,
    private val getCategories: GetCategoriesUseCase,
) : MviViewModel<CategoryChooserState, CategoryChooserEvent, CategoryChooserEffect>(
    initialState = CategoryChooserState.Loading(selectedId = initialSelectedId),
) {

    init {
        observeCategories()
    }

    override fun onEvent(event: CategoryChooserEvent) {
        when (event) {
            is CategoryChooserEvent.OnCategorySelected ->
                postSideEffect(CategoryChooserEffect.PublishResult(event.id))
            CategoryChooserEvent.OnCreateNewCategoryClick ->
                postSideEffect(CategoryChooserEffect.NavigateToCategoryCreation)
            CategoryChooserEvent.OnDismiss -> postSideEffect(CategoryChooserEffect.Dismiss)
        }
    }

    private fun observeCategories() {
        launch {
            getCategories().collect { categories ->
                val filtered = if (filterType != null) {
                    categories.filter { it.type == filterType }
                } else {
                    categories
                }
                updateState {
                    CategoryChooserState.Content(
                        categories = filtered,
                        selectedId = selectedId,
                    )
                }
            }
        }
    }
}

@optics
sealed interface CategoryChooserState {
    val selectedId: CategoryId?

    @optics
    data class Loading(override val selectedId: CategoryId?) : CategoryChooserState {
        companion object
    }

    @optics
    data class Content(
        val categories: List<Category>,
        override val selectedId: CategoryId?,
    ) : CategoryChooserState {
        companion object
    }

    companion object
}

sealed interface CategoryChooserEvent {
    data class OnCategorySelected(val id: CategoryId) : CategoryChooserEvent
    data object OnCreateNewCategoryClick : CategoryChooserEvent
    data object OnDismiss : CategoryChooserEvent
}

sealed interface CategoryChooserEffect {
    data object NavigateToCategoryCreation : CategoryChooserEffect
    data object Dismiss : CategoryChooserEffect
    data class PublishResult(val id: CategoryId) : CategoryChooserEffect
}
