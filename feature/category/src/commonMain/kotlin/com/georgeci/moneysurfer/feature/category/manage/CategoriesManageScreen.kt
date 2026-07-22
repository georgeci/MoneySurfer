package com.georgeci.moneysurfer.feature.category.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeRevealRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_add
import moneysurfer.feature.category.generated.resources.categories_manage_edit
import moneysurfer.feature.category.generated.resources.categories_manage_empty
import moneysurfer.feature.category.generated.resources.categories_manage_remove
import moneysurfer.feature.category.generated.resources.categories_manage_title
import moneysurfer.feature.category.generated.resources.category_creation_type_expense
import moneysurfer.feature.category.generated.resources.category_creation_type_income
import moneysurfer.feature.category.generated.resources.category_creation_type_transfer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Extra leading inset per nesting level. One level is all [CategoryTree.MAX_DEPTH] allows. */
private val ChildIndent = 24.dp

@Composable
fun CategoriesManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onNavigateToCategoryEdit: (CategoryId) -> Unit,
    onNavigateToCategoryDetails: (CategoryId) -> Unit = {},
    viewModel: CategoriesManageViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            CategoriesManageEffect.NavigateBack -> onNavigateBack()
            CategoriesManageEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is CategoriesManageEffect.NavigateToCategoryDetails ->
                onNavigateToCategoryDetails(effect.categoryId)
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
            val addLabel = stringResource(Res.string.categories_manage_add)
            ExtendedFloatingActionButton(
                text = { Text(addLabel) },
                icon = {
                    // decorative — FAB text label provides the accessible label
                    Icon(imageVector = SurferIcons.Add, contentDescription = null)
                },
                onClick = { onEvent(CategoriesManageEvent.OnAddCategoryClick) },
                modifier = Modifier.semantics { contentDescription = addLabel },
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
                top = AppTheme.spacing.small,
                bottom = padding.calculateBottomPadding() +
                    AppTheme.spacing.xxxLarge + AppTheme.spacing.xLarge,
            ),
        ) {
            items(state.categories, key = { it.id.value }) { category ->
                CategoryManageRow(
                    category = category,
                    deleteLabel = stringResource(Res.string.categories_manage_remove),
                    editLabel = stringResource(Res.string.categories_manage_edit),
                    onClick = { onEvent(CategoriesManageEvent.OnCategoryClick(category.id)) },
                    onEditClick = { onEvent(CategoriesManageEvent.OnEditCategoryClick(category.id)) },
                    onRemoveClick = { onEvent(CategoriesManageEvent.OnRemoveCategoryClick(category.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            // Children are indented under the parent they follow — the list is
                            // already in tree order, so the indent is all the nesting needs.
                            start = AppTheme.spacing.default + ChildIndent * category.depth,
                            end = AppTheme.spacing.default,
                        )
                        .padding(vertical = AppTheme.spacing.xSmall),
                )
            }
        }
    }

    state.pendingDelete?.let { pending ->
        DeleteCategoryDialog(
            categoryName = pending.name,
            childCount = pending.childCount,
            onConfirm = { onEvent(CategoriesManageEvent.OnDeleteConfirm) },
            onDismiss = { onEvent(CategoriesManageEvent.OnDeleteCancel) },
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: CategoryManageUi,
    deleteLabel: String,
    editLabel: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferSwipeRevealRow(
        // Two actions now that tapping the row opens the detail screen instead of the editor —
        // edit has to stay reachable from the list itself.
        revealWidth = 176.dp,
        modifier = modifier,
        actions = {
            SurferSwipeAction(
                icon = SurferIcons.Edit,
                label = editLabel,
                onClick = onEditClick,
            )
            SurferSwipeAction(
                icon = SurferIcons.Delete,
                label = deleteLabel,
                onClick = onRemoveClick,
                destructive = true,
            )
        },
    ) {
        CategoryManageCard(
            category = category,
            onClick = onClick,
        )
    }
}

@Composable
private fun CategoryManageCard(
    category: CategoryManageUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = SurferCategoryPalette.visualFor(
        id = category.id.value,
        iconKey = category.iconKey,
        hue = category.hue,
        systemKind = category.systemKind,
    )
    SurferCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = AppTheme.spacing.default,
                vertical = AppTheme.spacing.medium,
            ),
        ) {
            SurferCategoryBubble(
                icon = visual.icon,
                tint = visual.tint,
                size = 36.dp,
                modifier = Modifier.padding(end = AppTheme.spacing.medium),
            )
            Text(
                text = category.name,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = categoryTypeLabel(category.type),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Icon(
                imageVector = SurferIcons.ChevronRight,
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(start = AppTheme.spacing.small),
            )
        }
    }
}

@Composable
private fun categoryTypeLabel(type: CategoryType): String =
    when (type) {
        CategoryType.EXPENSE -> stringResource(Res.string.category_creation_type_expense)
        CategoryType.INCOME -> stringResource(Res.string.category_creation_type_income)
        CategoryType.TRANSFER -> stringResource(Res.string.category_creation_type_transfer)
    }

@Preview
@Composable
private fun CategoriesManageContentPreview() {
    AppTheme {
        CategoriesManageContent(
            state = CategoriesManageState.Content(
                categories = listOf(
                    CategoryManageUi(CategoryId("1"), "Food", CategoryType.EXPENSE),
                    CategoryManageUi(
                        id = CategoryId("2"),
                        name = "Groceries",
                        type = CategoryType.EXPENSE,
                        parentId = CategoryId("1"),
                        depth = 1,
                    ),
                    CategoryManageUi(CategoryId("3"), "Salary", CategoryType.INCOME),
                ),
            ),
            onEvent = {},
        )
    }
}
