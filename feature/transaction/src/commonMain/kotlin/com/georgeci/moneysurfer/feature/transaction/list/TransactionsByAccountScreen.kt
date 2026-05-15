package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.base.SurferFilterChipRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_filtered
import moneysurfer.feature.transaction.generated.resources.transactions_list_filter_all
import moneysurfer.feature.transaction.generated.resources.transactions_list_filter_expenses
import moneysurfer.feature.transaction.generated.resources.transactions_list_filter_income
import moneysurfer.feature.transaction.generated.resources.transactions_list_new
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_expenses
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_income
import moneysurfer.feature.transaction.generated.resources.transactions_list_summary_net
import moneysurfer.feature.transaction.generated.resources.transactions_list_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionsByAccountScreen(
    accountId: AccountId?,
    onNavigateBack: () -> Unit,
    onNavigateToTransactionCreation: (AccountId?) -> Unit,
    onNavigateToTransactionDetails: (TransactionId) -> Unit,
    viewModel: TransactionsByAccountViewModel = koinViewModel(
        key = accountId?.value.orEmpty(),
    ) { parametersOf(accountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionsByAccountEffect.NavigateBack -> onNavigateBack()
            is TransactionsByAccountEffect.NavigateToTransactionCreation ->
                onNavigateToTransactionCreation(effect.accountId)
            is TransactionsByAccountEffect.NavigateToTransactionDetails ->
                onNavigateToTransactionDetails(effect.transactionId)
        }
    }

    when (val current = state) {
        is TransactionsByAccountState.Loading -> TransactionsByAccountLoading(
            onEvent = viewModel::onEvent,
        )
        is TransactionsByAccountState.Content -> TransactionsByAccountContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun TransactionsByAccountLoading(onEvent: (TransactionsByAccountEvent) -> Unit) {
    val titleFallback = stringResource(Res.string.transactions_list_title)
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = titleFallback,
                onBack = { onEvent(TransactionsByAccountEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun TransactionsByAccountContent(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    val titleFallback = stringResource(Res.string.transactions_list_title)
    val title = state.accountName.ifBlank { titleFallback }
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = title,
                onBack = { onEvent(TransactionsByAccountEvent.OnBackClick) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(Res.string.transactions_list_new)) },
                icon = { Icon(imageVector = SurferIcons.Add, contentDescription = null) },
                onClick = { onEvent(TransactionsByAccountEvent.OnAddTransactionClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            SummaryStrip(
                summary = state.summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            TypeFilterChips(
                selected = state.typeFilter,
                onSelect = { onEvent(TransactionsByAccountEvent.OnTypeFilterChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(4.dp))

            if (state.isEmpty) {
                EmptyState(isFiltered = state.isFiltered)
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                state.groups.forEach { group ->
                    item(key = "h-${group.dateLabel}") {
                        DateHeader(group = group)
                    }
                    group.transactions.forEach { row ->
                        item(key = "t-${row.id.value}") {
                            val amountColor = if (row.isExpense) {
                                AppTheme.materialColors.onSurface
                            } else {
                                AppTheme.semanticColors.income
                            }
                            SurferTransactionLine(
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp),
                                icon = if (row.isExpense) SurferIcons.Receipt else SurferIcons.Wallet,
                                title = row.title,
                                formattedAmount = row.formattedAmount,
                                categoryHueSeed = row.categoryHueSeed,
                                meta = row.subtitle.takeIf { it.isNotEmpty() },
                                amountColor = amountColor,
                                amountPillBackground = if (row.isExpense) {
                                    null
                                } else {
                                    AppTheme.semanticColors.income.copy(alpha = 0.14f)
                                },
                                onClick = { onEvent(TransactionsByAccountEvent.OnTransactionClick(row.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(
    summary: TransactionSummaryUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_income),
            value = summary.incomeFormatted,
            valueColor = AppTheme.semanticColors.income,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_expenses),
            value = summary.expenseFormatted,
            valueColor = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        SummaryDivider()
        SummaryCell(
            label = stringResource(Res.string.transactions_list_summary_net),
            value = summary.netFormatted,
            valueColor = if (summary.netPositive) {
                AppTheme.semanticColors.income
            } else {
                AppTheme.materialColors.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = AppTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(28.dp)
            .background(AppTheme.materialColors.outlineVariant),
    )
}

@Composable
private fun TypeFilterChips(
    selected: TransactionTypeFilter,
    onSelect: (TransactionTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelAll = stringResource(Res.string.transactions_list_filter_all)
    val labelExpenses = stringResource(Res.string.transactions_list_filter_expenses)
    val labelIncome = stringResource(Res.string.transactions_list_filter_income)
    SurferFilterChipRow(
        modifier = modifier,
        options = listOf(
            TransactionTypeFilter.All,
            TransactionTypeFilter.Expenses,
            TransactionTypeFilter.Income,
        ),
        selected = selected,
        label = {
            when (it) {
                TransactionTypeFilter.All -> labelAll
                TransactionTypeFilter.Expenses -> labelExpenses
                TransactionTypeFilter.Income -> labelIncome
            }
        },
        onSelect = onSelect,
    )
}

@Composable
private fun EmptyState(isFiltered: Boolean) {
    val text = if (isFiltered) {
        stringResource(Res.string.transactions_list_empty_filtered)
    } else {
        stringResource(Res.string.transactions_list_empty)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DateHeader(group: TransactionGroupUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = group.dateLabel,
            style = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = group.netFormatted,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun TransactionsByAccountPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Everyday",
                groups = listOf(
                    TransactionGroupUi(
                        dateLabel = "Today",
                        netFormatted = "−€72.70",
                        netPositive = false,
                        transactions = listOf(
                            TransactionRowUi(
                                id = TransactionId("preview-tx-1"),
                                title = "Lidl — weekly shop",
                                subtitle = "Groceries",
                                formattedAmount = "−€48.20",
                                isExpense = true,
                                categoryHueSeed = "preview-cat-1",
                            ),
                            TransactionRowUi(
                                id = TransactionId("preview-tx-2"),
                                title = "Ramen with J.",
                                subtitle = "Dining",
                                formattedAmount = "−€24.50",
                                isExpense = true,
                                categoryHueSeed = "preview-cat-2",
                            ),
                        ),
                    ),
                    TransactionGroupUi(
                        dateLabel = "Yesterday",
                        netFormatted = "+€3,200.00",
                        netPositive = true,
                        transactions = listOf(
                            TransactionRowUi(
                                id = TransactionId("preview-tx-3"),
                                title = "March payroll",
                                subtitle = "Salary",
                                formattedAmount = "+€3,200.00",
                                isExpense = false,
                                categoryHueSeed = "preview-cat-3",
                            ),
                        ),
                    ),
                ),
                summary = TransactionSummaryUi(
                    incomeFormatted = "+€3,200.00",
                    expenseFormatted = "−€72.70",
                    netFormatted = "+€3,127.30",
                    netPositive = true,
                ),
                typeFilter = TransactionTypeFilter.All,
                isFiltered = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionsByAccountEmptyPreview() {
    AppTheme {
        TransactionsByAccountContent(
            state = TransactionsByAccountState.Content(
                accountId = AccountId("preview-acc-1"),
                accountName = "Savings",
                groups = emptyList(),
                summary = TransactionSummaryUi(
                    incomeFormatted = "+€0.00",
                    expenseFormatted = "−€0.00",
                    netFormatted = "+€0.00",
                    netPositive = true,
                ),
                typeFilter = TransactionTypeFilter.All,
                isFiltered = false,
            ),
            onEvent = {},
        )
    }
}
