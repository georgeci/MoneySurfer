package com.georgeci.moneysurfer.feature.budget.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.feature.budget.BudgetUi
import com.georgeci.moneysurfer.feature.budget.budgetPeriodLabel
import com.georgeci.moneysurfer.feature.budget.budgetStatusLabel
import com.georgeci.moneysurfer.feature.budget.previewBudget
import com.georgeci.moneysurfer.feature.budget.toUi
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeRevealRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetCard
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetCardMetrics
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetCategoryVisual
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budget_days_left
import moneysurfer.feature.budget.generated.resources.budget_footer_format
import moneysurfer.feature.budget.generated.resources.budget_over_format
import moneysurfer.feature.budget.generated.resources.budget_remaining_format
import moneysurfer.feature.budget.generated.resources.budget_spent_of_format
import moneysurfer.feature.budget.generated.resources.budgets_active_header
import moneysurfer.feature.budget.generated.resources.budgets_add
import moneysurfer.feature.budget.generated.resources.budgets_archive
import moneysurfer.feature.budget.generated.resources.budgets_archived_header
import moneysurfer.feature.budget.generated.resources.budgets_delete
import moneysurfer.feature.budget.generated.resources.budgets_delete_cancel
import moneysurfer.feature.budget.generated.resources.budgets_delete_message
import moneysurfer.feature.budget.generated.resources.budgets_delete_title
import moneysurfer.feature.budget.generated.resources.budgets_empty
import moneysurfer.feature.budget.generated.resources.budgets_restore
import moneysurfer.feature.budget.generated.resources.budgets_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the Budgets screen — see docs/testing/testing-strategy.md. */
object BudgetsTestTags {
    const val Root = "budgets:root"
    const val AddButton = "budgets:add"
    const val List = "budgets:list"
}

@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBudgetCreation: () -> Unit,
    onNavigateToBudgetDetails: (BudgetId) -> Unit,
    viewModel: BudgetsViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BudgetsEffect.NavigateBack -> onNavigateBack()
            BudgetsEffect.NavigateToBudgetCreation -> onNavigateToBudgetCreation()
            is BudgetsEffect.NavigateToBudgetDetails -> onNavigateToBudgetDetails(effect.budgetId)
        }
    }

    BudgetsContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun BudgetsContent(
    state: BudgetsState,
    onEvent: (BudgetsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(BudgetsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.budgets_title),
                onBack = { onEvent(BudgetsEvent.OnBackClick) },
            )
        },
        floatingActionButton = {
            val addLabel = stringResource(Res.string.budgets_add)
            ExtendedFloatingActionButton(
                text = { Text(addLabel) },
                icon = {
                    Icon(imageVector = SurferIcons.Add, contentDescription = SurferSemantics.Decorative)
                },
                onClick = { onEvent(BudgetsEvent.OnAddBudgetClick) },
                modifier = Modifier
                    .semantics { contentDescription = addLabel }
                    .testTag(BudgetsTestTags.AddButton),
            )
        },
    ) { padding ->
        val content = state as? BudgetsState.Content
        if (content == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (content.active.isEmpty() && content.archived.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AppTheme.spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.budgets_empty),
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
                .padding(top = padding.calculateTopPadding())
                .testTag(BudgetsTestTags.List),
            contentPadding = PaddingValues(
                top = AppTheme.spacing.small,
                bottom = padding.calculateBottomPadding() +
                    AppTheme.spacing.xxxLarge + AppTheme.spacing.xLarge,
            ),
        ) {
            if (content.active.isNotEmpty() && content.archived.isNotEmpty()) {
                item(key = "header:active") {
                    SectionHeader(stringResource(Res.string.budgets_active_header))
                }
            }
            items(content.active, key = { it.id.value }) { budget ->
                BudgetRow(
                    budget = budget,
                    archiveLabel = stringResource(Res.string.budgets_archive),
                    deleteLabel = stringResource(Res.string.budgets_delete),
                    onClick = { onEvent(BudgetsEvent.OnBudgetClick(budget.id)) },
                    onArchiveClick = { onEvent(BudgetsEvent.OnArchiveClick(budget.id)) },
                    onDeleteClick = { onEvent(BudgetsEvent.OnDeleteClick(budget.id)) },
                )
            }
            if (content.archived.isNotEmpty()) {
                item(key = "header:archived") {
                    SectionHeader(stringResource(Res.string.budgets_archived_header))
                }
                items(content.archived, key = { it.id.value }) { budget ->
                    BudgetRow(
                        budget = budget,
                        archiveLabel = stringResource(Res.string.budgets_restore),
                        deleteLabel = stringResource(Res.string.budgets_delete),
                        onClick = { onEvent(BudgetsEvent.OnBudgetClick(budget.id)) },
                        onArchiveClick = { onEvent(BudgetsEvent.OnRestoreClick(budget.id)) },
                        onDeleteClick = { onEvent(BudgetsEvent.OnDeleteClick(budget.id)) },
                    )
                }
            }
        }
    }

    (state as? BudgetsState.Content)?.pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { onEvent(BudgetsEvent.OnDeleteCancel) },
            title = { Text(stringResource(Res.string.budgets_delete_title)) },
            text = { Text(stringResource(Res.string.budgets_delete_message, pending.name)) },
            confirmButton = {
                TextButton(onClick = { onEvent(BudgetsEvent.OnDeleteConfirm) }) {
                    Text(stringResource(Res.string.budgets_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(BudgetsEvent.OnDeleteCancel) }) {
                    Text(stringResource(Res.string.budgets_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.labelLarge,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = Modifier.padding(
            start = AppTheme.spacing.default,
            end = AppTheme.spacing.default,
            top = AppTheme.spacing.medium,
            bottom = AppTheme.spacing.xSmall,
        ),
    )
}

@Composable
private fun BudgetRow(
    budget: BudgetUi,
    archiveLabel: String,
    deleteLabel: String,
    onClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    SurferSwipeRevealRow(
        revealWidth = 176.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.default)
            .padding(vertical = AppTheme.spacing.xSmall),
        actions = {
            SurferSwipeAction(
                icon = if (budget.isActive) SurferIcons.Archive else SurferIcons.Restore,
                label = archiveLabel,
                onClick = onArchiveClick,
            )
            SurferSwipeAction(
                icon = SurferIcons.Delete,
                label = deleteLabel,
                onClick = onDeleteClick,
                destructive = true,
            )
        },
    ) {
        SurferBudgetCard(
            name = budget.name,
            statusLabel = budgetStatusLabel(budget.status),
            status = budget.status.toUi(),
            metrics = SurferBudgetCardMetrics(
                spentOfLimit = stringResource(
                    Res.string.budget_spent_of_format,
                    budget.spentFormatted,
                    budget.limitFormatted,
                ),
                remaining = if (budget.isOver) {
                    stringResource(Res.string.budget_over_format, budget.remainderFormatted)
                } else {
                    stringResource(Res.string.budget_remaining_format, budget.remainderFormatted)
                },
                progress = budget.progress,
                footer = stringResource(
                    Res.string.budget_footer_format,
                    budgetPeriodLabel(budget.period),
                    pluralStringResource(Res.plurals.budget_days_left, budget.daysLeft, budget.daysLeft),
                ),
                alertFraction = budget.alertFraction,
            ),
            categories = budget.categories.map { category ->
                val visual = SurferCategoryPalette.visualFor(
                    id = category.id,
                    iconKey = category.iconKey,
                    hue = category.hue,
                    systemKind = category.systemKind,
                )
                SurferBudgetCategoryVisual(icon = visual.icon, tint = visual.tint)
            },
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun BudgetsContentPreview() {
    AppTheme {
        BudgetsContent(
            state = BudgetsState.Content(
                active = listOf(previewBudget()),
                archived = emptyList(),
            ),
            onEvent = {},
        )
    }
}
