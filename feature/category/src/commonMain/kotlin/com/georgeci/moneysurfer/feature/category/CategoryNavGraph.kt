package com.georgeci.moneysurfer.feature.category

import androidx.compose.material3.ExperimentalMaterial3Api
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.feature.category.creation.CategoryCreationScreen
import com.georgeci.moneysurfer.feature.category.manage.CategoriesManageScreen
import com.georgeci.moneysurfer.feature.category.picker.CategoryChooserBottomSheet
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.CategoryPickerResultKey
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route
import io.github.irgaly.navigation3.resultstate.LocalNavigationResultProducer
import io.github.irgaly.navigation3.resultstate.setResult

@OptIn(ExperimentalMaterial3Api::class)
val categoryNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.CategoryCreation> {
        CategoryCreationScreen(
            categoryId = it.categoryId?.let(::CategoryId),
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.CategoriesManage> {
        CategoriesManageScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToCategoryCreation = { navigator.push(Route.CategoryCreation()) },
            onNavigateToCategoryEdit = { categoryId ->
                navigator.push(Route.CategoryCreation(categoryId = categoryId.value))
            },
        )
    }

    entry<Route.CategoryChooser>(
        metadata = BottomSheetSceneStrategy.bottomSheet(),
    ) { key ->
        val resultProducer = LocalNavigationResultProducer.current
        CategoryChooserBottomSheet(
            selectedCategoryId = key.selectedCategoryId?.let { CategoryId(it) },
            filterType = key.filterType?.let { runCatching { CategoryType.valueOf(it) }.getOrNull() },
            onDismiss = { navigator.pop() },
            onNavigateToCategoryCreation = {
                navigator.replaceTop(Route.CategoryCreation())
            },
            onCategoryPicked = { picked ->
                resultProducer.setResult(CategoryPickerResultKey, picked)
                navigator.pop()
            },
        )
    }
}
