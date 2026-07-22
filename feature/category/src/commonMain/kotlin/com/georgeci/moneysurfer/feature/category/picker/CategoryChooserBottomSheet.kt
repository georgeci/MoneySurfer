package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.transaction_creation_category_create_new
import moneysurfer.feature.category.generated.resources.transaction_creation_category_search
import moneysurfer.feature.category.generated.resources.transaction_creation_category_section_expense
import moneysurfer.feature.category.generated.resources.transaction_creation_category_section_income
import moneysurfer.feature.category.generated.resources.transaction_creation_category_sheet_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Bottom-sheet entry composable for picking a category. Hosted as a navigation entry; the
 * surrounding [androidx.compose.material3.ModalBottomSheet] is provided by
 * [com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy]. Visual layout lives
 * in [CategoryChooserContent] — this composable just maps the ViewModel's state and effects.
 */
@Composable
fun CategoryChooserBottomSheet(
    selectedCategoryId: CategoryId?,
    filterType: CategoryType?,
    onDismiss: () -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onCategoryPicked: (CategoryId) -> Unit,
    viewModel: CategoryChooserViewModel = koinViewModel(
        key = "${selectedCategoryId?.value}:${filterType?.name}",
    ) { parametersOf(selectedCategoryId, filterType) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoryChooserEffect.Dismiss -> onDismiss()
            CategoryChooserEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is CategoryChooserEffect.PublishResult -> onCategoryPicked(effect.id)
        }
    }

    val categories = (state as? CategoryChooserState.Content)?.categories.orEmpty()

    CategoryChooserContent(
        title = stringResource(Res.string.transaction_creation_category_sheet_title),
        searchPlaceholder = stringResource(Res.string.transaction_creation_category_search),
        categories = categories.map { cat ->
            val visual = SurferCategoryPalette.visualFor(
                id = cat.id.value,
                iconKey = cat.iconKey,
                hue = cat.hue,
                systemKind = cat.systemKind?.name,
            )
            CategoryPickerRow(
                id = cat.id.value,
                name = cat.name,
                icon = visual.icon,
                tint = visual.tint,
                isIncome = false,
            )
        },
        selectedId = state.selectedId?.value,
        expenseSectionTitle = stringResource(Res.string.transaction_creation_category_section_expense),
        incomeSectionTitle = stringResource(Res.string.transaction_creation_category_section_income),
        onSelect = { row ->
            viewModel.onEvent(CategoryChooserEvent.OnCategorySelected(CategoryId(row.id)))
        },
        createNewLabel = stringResource(Res.string.transaction_creation_category_create_new),
        onCreateNewClick = { viewModel.onEvent(CategoryChooserEvent.OnCreateNewCategoryClick) },
    )
}
