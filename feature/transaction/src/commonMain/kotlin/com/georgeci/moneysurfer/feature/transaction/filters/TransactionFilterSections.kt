package com.georgeci.moneysurfer.feature.transaction.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_filters_amount_max
import moneysurfer.feature.transaction.generated.resources.transaction_filters_amount_min
import moneysurfer.feature.transaction.generated.resources.transaction_filters_categories_all
import moneysurfer.feature.transaction.generated.resources.transaction_filters_categories_counter
import moneysurfer.feature.transaction.generated.resources.transaction_filters_planned_only
import moneysurfer.feature.transaction.generated.resources.transaction_filters_planned_only_hint
import moneysurfer.feature.transaction.generated.resources.transaction_filters_recurring_only
import moneysurfer.feature.transaction.generated.resources.transaction_filters_recurring_only_hint
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_accounts
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_amount
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_categories
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_other
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_sort
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_type
import moneysurfer.feature.transaction.generated.resources.transaction_filters_sort_newest
import moneysurfer.feature.transaction.generated.resources.transaction_filters_sort_oldest
import moneysurfer.feature.transaction.generated.resources.transaction_filters_type_all
import moneysurfer.feature.transaction.generated.resources.transaction_filters_type_expenses
import moneysurfer.feature.transaction.generated.resources.transaction_filters_type_income
import moneysurfer.feature.transaction.generated.resources.transaction_filters_type_transfer
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FilterSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = AppTheme.typography.labelSmall,
        color = AppTheme.materialColors.onSurfaceVariant,
    )
}

@Composable
internal fun TypeSection(
    type: TransactionTypeFilter,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    val labels = mapOf(
        TransactionTypeFilter.All to stringResource(Res.string.transaction_filters_type_all),
        TransactionTypeFilter.Expenses to stringResource(Res.string.transaction_filters_type_expenses),
        TransactionTypeFilter.Income to stringResource(Res.string.transaction_filters_type_income),
        TransactionTypeFilter.Transfer to stringResource(Res.string.transaction_filters_type_transfer),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_type))
        SurferSegmentedControl(
            options = TransactionTypeFilter.entries,
            selected = type,
            label = { labels.getValue(it) },
            onSelect = { onEvent(TransactionFiltersEvent.OnTypeSelected(it)) },
        )
    }
}

/**
 * Multi-select over the accounts. An empty selection means "every account", the same convention
 * the category chips use — there is no separate "all" tile to keep in sync with the checkboxes.
 */
@Composable
internal fun AccountsSection(
    accounts: List<Account>,
    selected: Set<AccountId>,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_accounts))
        accounts.forEach { account ->
            AccountTile(
                name = account.name,
                checked = account.id in selected,
                onToggle = { onEvent(TransactionFiltersEvent.OnAccountToggled(account.id)) },
            )
        }
    }
}

@Composable
private fun AccountTile(
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.large)
            .background(AppTheme.materialColors.surfaceContainerLow)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoriesSection(
    categories: List<Category>,
    selected: Set<CategoryId>,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterSectionHeader(stringResource(Res.string.transaction_filters_section_categories))
            Text(
                text = stringResource(
                    Res.string.transaction_filters_categories_counter,
                    selected.size,
                    categories.size,
                ),
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = { onEvent(TransactionFiltersEvent.OnAllCategoriesClick) },
                label = { Text(stringResource(Res.string.transaction_filters_categories_all)) },
            )
            categories.forEach { category ->
                FilterChip(
                    selected = category.id in selected,
                    onClick = { onEvent(TransactionFiltersEvent.OnCategoryToggled(category.id)) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@Composable
internal fun AmountSection(
    minAmount: String,
    maxAmount: String,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_amount))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountField(
                label = stringResource(Res.string.transaction_filters_amount_min),
                value = minAmount,
                onChange = { onEvent(TransactionFiltersEvent.OnMinAmountChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            AmountField(
                label = stringResource(Res.string.transaction_filters_amount_max),
                value = maxAmount,
                onChange = { onEvent(TransactionFiltersEvent.OnMaxAmountChanged(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
internal fun OtherSection(
    filters: TransactionFilters,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_other))
        ToggleRow(
            title = stringResource(Res.string.transaction_filters_recurring_only),
            hint = stringResource(Res.string.transaction_filters_recurring_only_hint),
            checked = filters.recurringOnly,
            onChange = { onEvent(TransactionFiltersEvent.OnRecurringOnlyChanged(it)) },
        )
        ToggleRow(
            title = stringResource(Res.string.transaction_filters_planned_only),
            hint = stringResource(Res.string.transaction_filters_planned_only_hint),
            checked = filters.plannedOnly,
            onChange = { onEvent(TransactionFiltersEvent.OnPlannedOnlyChanged(it)) },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = hint,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
        SurferSettingsSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SortSection(
    sort: TransactionSort,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    val labels = mapOf(
        TransactionSort.Newest to stringResource(Res.string.transaction_filters_sort_newest),
        TransactionSort.Oldest to stringResource(Res.string.transaction_filters_sort_oldest),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_sort))
        SurferSegmentedControl(
            options = TransactionSort.entries,
            selected = sort,
            label = { labels.getValue(it) },
            onSelect = { onEvent(TransactionFiltersEvent.OnSortSelected(it)) },
        )
    }
}
