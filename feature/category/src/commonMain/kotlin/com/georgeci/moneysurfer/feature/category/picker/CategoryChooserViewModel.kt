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
    private val initialVariant: CategoryPickerVariant,
    private val getCategories: GetCategoriesUseCase,
) : MviViewModel<CategoryChooserState, CategoryChooserEvent, CategoryChooserEffect>(
    initialState = CategoryChooserState.Loading(selectedId = initialSelectedId),
) {

    /**
     * Every category of every type, unfiltered. The tab row switches type without another read,
     * and the state only ever carries what the current tab renders.
     */
    private var allCategories: List<Category> = emptyList()

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
            is CategoryChooserEvent.OnTypeSelected -> updateContent {
                // Expansion is per-parent and the parents differ per type, so a stale set would
                // only ever open the wrong rows.
                copy(type = event.type, expandedIds = expandedFor(event.type))
            }
            is CategoryChooserEvent.OnQueryChange -> updateContent { copy(query = event.query) }
            CategoryChooserEvent.OnVariantToggle -> updateContent { copy(variant = variant.other()) }
            is CategoryChooserEvent.OnParentExpandToggle -> updateContent {
                copy(
                    expandedIds = if (event.id in expandedIds) {
                        expandedIds - event.id
                    } else {
                        expandedIds + event.id
                    },
                )
            }
        }
    }

    private fun observeCategories() {
        launch {
            getCategories().collect { categories ->
                allCategories = categories
                updateState {
                    when (this) {
                        // Type, query and expansion are the user's; a re-emission from sync
                        // rebuilds the rows underneath them without resetting any of it.
                        is CategoryChooserState.Content -> this
                        is CategoryChooserState.Loading -> newContent()
                    }.rebuild()
                }
            }
        }
    }

    private fun CategoryChooserState.Loading.newContent(): CategoryChooserState.Content {
        val type = CategoryPickerRows.initialType(allCategories, selectedId, filterType)
        return CategoryChooserState.Content(
            selectedId = selectedId,
            selectedName = null,
            type = type,
            typeLocked = filterType != null,
            query = "",
            expandedIds = expandedFor(type),
            variant = initialVariant,
        )
    }

    private fun CategoryPickerVariant.other(): CategoryPickerVariant = when (this) {
        CategoryPickerVariant.GRID -> CategoryPickerVariant.TREE
        CategoryPickerVariant.TREE -> CategoryPickerVariant.GRID
    }

    private fun expandedFor(type: CategoryType): Set<CategoryId> {
        val selected = currentState.selectedId?.takeIf { id ->
            allCategories.firstOrNull { it.id == id }?.type == type
        }
        return CategoryPickerRows.expandedForSelection(allCategories, selected)
    }

    private fun updateContent(reducer: CategoryChooserState.Content.() -> CategoryChooserState.Content) =
        updateState { (this as? CategoryChooserState.Content)?.reducer()?.rebuild() ?: this }

    /** Re-derives everything the sheet draws from the inputs the events just changed. */
    private fun CategoryChooserState.Content.rebuild() = copy(
        selectedName = allCategories.firstOrNull { it.id == selectedId }?.name,
        gridItems = CategoryPickerRows.grid(allCategories, type, query, selectedId),
        treeItems = CategoryPickerRows.tree(allCategories, type, query, selectedId, expandedIds),
    )
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
        override val selectedId: CategoryId?,
        /** Name behind [selectedId], for the `Currently: …` subline. Null until it resolves. */
        val selectedName: String?,
        val type: CategoryType,
        /** True when the caller pinned the type — the tab row is then not offered at all. */
        val typeLocked: Boolean,
        val query: String,
        val expandedIds: Set<CategoryId>,
        val variant: CategoryPickerVariant,
        val gridItems: List<CategoryPickerItem> = emptyList(),
        val treeItems: List<CategoryTreeGroup> = emptyList(),
    ) : CategoryChooserState {
        companion object
    }

    companion object
}

sealed interface CategoryChooserEvent {
    data class OnCategorySelected(val id: CategoryId) : CategoryChooserEvent
    data class OnTypeSelected(val type: CategoryType) : CategoryChooserEvent
    data class OnQueryChange(val query: String) : CategoryChooserEvent
    data class OnParentExpandToggle(val id: CategoryId) : CategoryChooserEvent
    data object OnVariantToggle : CategoryChooserEvent
    data object OnCreateNewCategoryClick : CategoryChooserEvent
    data object OnDismiss : CategoryChooserEvent
}

sealed interface CategoryChooserEffect {
    data object NavigateToCategoryCreation : CategoryChooserEffect
    data object Dismiss : CategoryChooserEffect
    data class PublishResult(val id: CategoryId) : CategoryChooserEffect
}
