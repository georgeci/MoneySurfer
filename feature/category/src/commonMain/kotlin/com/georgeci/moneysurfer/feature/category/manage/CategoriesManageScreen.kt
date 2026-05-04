package com.georgeci.moneysurfer.feature.category.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeRevealRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_add
import moneysurfer.feature.category.generated.resources.categories_manage_empty
import moneysurfer.feature.category.generated.resources.categories_manage_remove
import moneysurfer.feature.category.generated.resources.categories_manage_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onNavigateToCategoryEdit: (CategoryId) -> Unit,
    viewModel: CategoriesManageViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoriesManageEffect.NavigateBack -> onNavigateBack()
            CategoriesManageEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is CategoriesManageEffect.NavigateToCategoryEdit -> onNavigateToCategoryEdit(effect.categoryId)
        }
    }

    when (val current = state) {
        CategoriesManageState.Loading -> CategoriesManageLoading(onEvent = viewModel::onEvent)
        is CategoriesManageState.Content -> CategoriesManageContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun CategoriesManageLoading(onEvent: (CategoriesManageEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.categories_manage_title),
                onBack = { onEvent(CategoriesManageEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun CategoriesManageContent(
    state: CategoriesManageState.Content,
    onEvent: (CategoriesManageEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.categories_manage_title),
                onBack = { onEvent(CategoriesManageEvent.OnBackClick) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(Res.string.categories_manage_add)) },
                icon = { Icon(imageVector = SurferIcons.Add, contentDescription = null) },
                onClick = { onEvent(CategoriesManageEvent.OnAddCategoryClick) },
            )
        },
    ) { padding ->
        if (state.categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AppTheme.spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.categories_manage_empty),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            items(state.categories, key = { it.id.value }) { category ->
                CategoryManageRow(
                    category = category,
                    deleteLabel = stringResource(Res.string.categories_manage_remove),
                    onClick = { onEvent(CategoriesManageEvent.OnCategoryClick(category.id)) },
                    onRemoveClick = { onEvent(CategoriesManageEvent.OnRemoveCategoryClick(category.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }

    state.pendingDelete?.let { pending ->
        DeleteCategoryDialog(
            categoryName = pending.name,
            onConfirm = { onEvent(CategoriesManageEvent.OnDeleteConfirm) },
            onDismiss = { onEvent(CategoriesManageEvent.OnDeleteCancel) },
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: CategoryManageUi,
    deleteLabel: String,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferSwipeRevealRow(
        revealWidth = 88.dp,
        modifier = modifier,
        actions = {
            SurferSwipeAction(
                icon = SurferIcons.Delete,
                label = deleteLabel,
                onClick = onRemoveClick,
                destructive = true,
            )
        },
    ) {
        CategoryManageCard(
            name = category.name,
            type = category.type,
            onClick = onClick,
        )
    }
}

@Composable
private fun CategoryManageCard(
    name: String,
    type: CategoryType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = name,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Icon(
                imageVector = SurferIcons.ChevronRight,
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun CategoriesManageContentPreview() {
    AppTheme {
        CategoriesManageContent(
            state = CategoriesManageState.Content(
                categories = listOf(
                    CategoryManageUi(CategoryId("1"), "Food", CategoryType.EXPENSE),
                    CategoryManageUi(CategoryId("2"), "Transport", CategoryType.EXPENSE),
                    CategoryManageUi(CategoryId("3"), "Salary", CategoryType.INCOME),
                ),
            ),
            onEvent = {},
        )
    }
}
