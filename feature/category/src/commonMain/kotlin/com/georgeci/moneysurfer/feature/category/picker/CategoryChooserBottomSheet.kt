package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.utils.HandleSideEffect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Bottom-sheet entry composable for picking a category. Hosted as a navigation entry; the
 * surrounding [androidx.compose.material3.ModalBottomSheet] is provided by
 * [com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy]. Visual layout lives
 * in [CategoryChooserContent] — this composable just maps the ViewModel's state and effects.
 *
 * [variant] chooses between the two designed layouts, so a caller that knows its user is hunting
 * for one name gets the flat grid, and one browsing a deep tree gets the expandable cards.
 */
@Composable
fun CategoryChooserBottomSheet(
    selectedCategoryId: CategoryId?,
    filterType: CategoryType?,
    onDismiss: () -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onCategoryPicked: (CategoryId) -> Unit,
    variant: CategoryPickerVariant = CategoryPickerVariant.GRID,
    viewModel: CategoryChooserViewModel = koinViewModel(
        key = "${selectedCategoryId?.value}:${filterType?.name}:${variant.name}",
    ) { parametersOf(selectedCategoryId, filterType, variant) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoryChooserEffect.Dismiss -> onDismiss()
            CategoryChooserEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is CategoryChooserEffect.PublishResult -> onCategoryPicked(effect.id)
        }
    }

    // Loading is a single frame before the local read lands; drawing the chrome around an empty
    // body would only flash the tab row into place.
    (state as? CategoryChooserState.Content)?.let { content ->
        CategoryChooserContent(state = content, onEvent = viewModel::onEvent)
    }
}
