package com.georgeci.moneysurfer.feature.budget.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.budget.BudgetUi
import com.georgeci.moneysurfer.feature.budget.budgetPeriodLabel
import com.georgeci.moneysurfer.feature.budget.budgetStatusLabel
import com.georgeci.moneysurfer.feature.budget.previewBudget
import com.georgeci.moneysurfer.feature.budget.toUi
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.components.budget.SurferAlertBanner
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetRing
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatusPill
import com.georgeci.moneysurfer.uikit.components.budget.SurferStatTile
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.budget.generated.resources.Res
import moneysurfer.feature.budget.generated.resources.budget_days_elapsed
import moneysurfer.feature.budget.generated.resources.budget_days_left
import moneysurfer.feature.budget.generated.resources.budget_details_alert_over_format
import moneysurfer.feature.budget.generated.resources.budget_details_alert_over_message
import moneysurfer.feature.budget.generated.resources.budget_details_alert_warn_format
import moneysurfer.feature.budget.generated.resources.budget_details_alert_warn_message_format
import moneysurfer.feature.budget.generated.resources.budget_details_edit
import moneysurfer.feature.budget.generated.resources.budget_details_not_found
import moneysurfer.feature.budget.generated.resources.budget_details_of_limit_format
import moneysurfer.feature.budget.generated.resources.budget_details_rollover_carry_format
import moneysurfer.feature.budget.generated.resources.budget_details_stat_daily_average
import moneysurfer.feature.budget.generated.resources.budget_details_stat_per_day
import moneysurfer.feature.budget.generated.resources.budget_details_stat_projected
import moneysurfer.feature.budget.generated.resources.budget_details_title
import moneysurfer.feature.budget.generated.resources.budget_details_transactions_empty
import moneysurfer.feature.budget.generated.resources.budget_details_transactions_header
import moneysurfer.feature.budget.generated.resources.budget_footer_format
import moneysurfer.feature.budget.generated.resources.budget_mixed_currency
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the Budget details screen — see docs/testing/testing-strategy.md. */
object BudgetDetailsTestTags {
    const val Root = "budgetDetails:root"
    const val Ring = "budgetDetails:ring"
}

@Composable
fun BudgetDetailsScreen(
    budgetId: BudgetId,
    onNavigateBack: () -> Unit,
    onNavigateToBudgetEdit: (BudgetId) -> Unit,
    onNavigateToTransactionDetails: (TransactionId) -> Unit,
    viewModel: BudgetDetailsViewModel = koinViewModel(key = budgetId.value) { parametersOf(budgetId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BudgetDetailsEffect.NavigateBack -> onNavigateBack()
            is BudgetDetailsEffect.NavigateToBudgetEdit -> onNavigateToBudgetEdit(effect.budgetId)
            is BudgetDetailsEffect.NavigateToTransactionDetails ->
                onNavigateToTransactionDetails(effect.transactionId)
        }
    }

    BudgetDetailsContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun BudgetDetailsContent(
    state: BudgetDetailsState,
    onEvent: (BudgetDetailsEvent) -> Unit,
) {
    val content = state as? BudgetDetailsState.Content
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(BudgetDetailsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = content?.budget?.name ?: stringResource(Res.string.budget_details_title),
                onBack = { onEvent(BudgetDetailsEvent.OnBackClick) },
                actions = {
                    if (content != null) {
                        SurferToolbarButtonAction(
                            icon = SurferIcons.Edit,
                            text = stringResource(Res.string.budget_details_edit),
                            onClick = { onEvent(BudgetDetailsEvent.OnEditClick) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state is BudgetDetailsState.Missing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .surferContentContainer()
                    .padding(padding)
                    .padding(AppTheme.spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.budget_details_not_found),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        if (content == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().surferContentContainer().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.default,
                end = AppTheme.spacing.default,
                top = AppTheme.spacing.medium,
                bottom = padding.calculateBottomPadding() + AppTheme.spacing.xLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            item(key = "hero") { BudgetHero(content.budget) }
            item(key = "alert") { BudgetAlert(content.budget) }
            item(key = "stats") { BudgetStats(content.budget) }
            item(key = "transactionsHeader") {
                Text(
                    text = stringResource(Res.string.budget_details_transactions_header),
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    modifier = Modifier.padding(top = AppTheme.spacing.small),
                )
            }
            if (content.transactions.isEmpty()) {
                item(key = "transactionsEmpty") {
                    Text(
                        text = stringResource(Res.string.budget_details_transactions_empty),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
            items(content.transactions, key = { it.id.value }) { transaction ->
                BudgetTransactionRow(
                    transaction = transaction,
                    onClick = { onEvent(BudgetDetailsEvent.OnTransactionClick(transaction.id)) },
                )
            }
        }
    }
}

@Composable
private fun BudgetHero(budget: BudgetUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        SurferBudgetRing(
            progress = budget.progress,
            status = budget.status.toUi(),
            modifier = Modifier.testTag(BudgetDetailsTestTags.Ring),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${budget.percent}%",
                    style = AppTheme.typography.headlineMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                Text(
                    text = budget.spentFormatted,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.budget_details_of_limit_format, budget.limitFormatted),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        SurferBudgetStatusPill(label = budgetStatusLabel(budget.status), status = budget.status.toUi())
        Text(
            text = stringResource(
                Res.string.budget_footer_format,
                "${budgetPeriodLabel(budget.period)} · ${budget.windowLabel}",
                pluralStringResource(Res.plurals.budget_days_left, budget.daysLeft, budget.daysLeft),
            ),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        budget.rolloverCarryFormatted?.let { carry ->
            Text(
                text = stringResource(Res.string.budget_details_rollover_carry_format, carry),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
        if (budget.hasMixedCurrency) {
            Text(
                text = stringResource(Res.string.budget_mixed_currency),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BudgetAlert(budget: BudgetUi) {
    when (budget.status) {
        BudgetStatus.OK -> Unit
        BudgetStatus.WARN -> SurferAlertBanner(
            title = stringResource(Res.string.budget_details_alert_warn_format, budget.percent),
            status = budget.status.toUi(),
            message = stringResource(
                Res.string.budget_details_alert_warn_message_format,
                budget.alertPercent,
            ),
        )
        BudgetStatus.OVER -> SurferAlertBanner(
            title = stringResource(Res.string.budget_details_alert_over_format, budget.remainderFormatted),
            status = budget.status.toUi(),
            message = stringResource(Res.string.budget_details_alert_over_message),
        )
    }
}

@Composable
private fun BudgetStats(budget: BudgetUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        SurferStatTile(
            label = stringResource(Res.string.budget_details_stat_daily_average),
            value = budget.dailyAverageFormatted,
            caption = pluralStringResource(
                Res.plurals.budget_days_elapsed,
                budget.elapsedDays,
                budget.elapsedDays,
            ),
            modifier = Modifier.weight(1f),
        )
        SurferStatTile(
            label = stringResource(Res.string.budget_details_stat_projected),
            value = budget.projectedTotalFormatted,
            valueColor = if (budget.status == BudgetStatus.OVER) AppTheme.materialColors.error else null,
            modifier = Modifier.weight(1f),
        )
        SurferStatTile(
            label = stringResource(Res.string.budget_details_stat_per_day),
            value = budget.perDayRemainingFormatted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BudgetTransactionRow(
    transaction: BudgetTransactionUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(transaction.dateLabel, transaction.categoryName).joinToString(" · "),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
        Text(
            text = transaction.amountFormatted,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Preview
@Composable
private fun BudgetDetailsContentPreview() {
    AppTheme {
        BudgetDetailsContent(
            state = BudgetDetailsState.Content(
                budget = previewBudget(),
                transactions = listOf(
                    BudgetTransactionUi(
                        id = TransactionId("t-1"),
                        title = "Lidl",
                        categoryName = "Groceries",
                        amountFormatted = "€42.10",
                        dateLabel = "18 Apr",
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
