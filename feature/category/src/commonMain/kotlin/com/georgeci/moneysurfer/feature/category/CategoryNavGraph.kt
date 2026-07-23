package com.georgeci.moneysurfer.feature.category

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.feature.category.creation.CategoryCreationScreen
import com.georgeci.moneysurfer.feature.category.details.CategoryDetailsScreen
import com.georgeci.moneysurfer.feature.category.manage.CategoriesManageScreen
import com.georgeci.moneysurfer.feature.category.picker.CategoryChooserBottomSheet
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.CategoryPickerResultKey
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import io.github.irgaly.navigation3.resultstate.LocalNavigationResultProducer
import io.github.irgaly.navigation3.resultstate.setResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
val categoryNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.CategoryCreation>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        CategoryCreationScreen(
            categoryId = it.categoryId?.let(::CategoryId),
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.CategoriesManage>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) {
        CategoriesManageScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToCategoryCreation = { navigator.push(Route.CategoryCreation()) },
            onNavigateToCategoryEdit = { categoryId ->
                navigator.push(Route.CategoryCreation(categoryId = categoryId.value))
            },
            onNavigateToCategoryDetails = { categoryId ->
                navigator.push(Route.CategoryDetails(categoryId = categoryId.value))
            },
        )
    }

    entry<Route.CategoryDetails>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { key ->
        CategoryDetailsScreen(
            categoryId = CategoryId(key.categoryId),
            onNavigateBack = { navigator.pop() },
            onNavigateToCategoryEdit = { categoryId ->
                navigator.push(Route.CategoryCreation(categoryId = categoryId.value))
            },
            // A subcategory opens its own detail page rather than replacing this one, so Back
            // walks back up the tree the user drilled down through.
            onNavigateToCategoryDetails = { categoryId ->
                navigator.push(Route.CategoryDetails(categoryId = categoryId.value))
            },
            onNavigateToTransactionCreation = { navigator.push(Route.TransactionCreation()) },
            onNavigateToTransactionDetails = { transactionId ->
                navigator.push(Route.TransactionDetails(transactionId.value))
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
