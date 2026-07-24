package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.uikit.components.SurferSearchField
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_last_month
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_month
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_week
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_year
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_today
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_yesterday
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_account
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_category
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_date
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_date_custom
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_selected_count
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_sort
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_type
import moneysurfer.feature.transaction.generated.resources.transactions_list_chip_value
import moneysurfer.feature.transaction.generated.resources.transactions_list_filter_expenses
import moneysurfer.feature.transaction.generated.resources.transactions_list_filter_income
import moneysurfer.feature.transaction.generated.resources.transactions_list_filters_badge
import moneysurfer.feature.transaction.generated.resources.transactions_list_filters_content_description
import moneysurfer.feature.transaction.generated.resources.transactions_list_search_placeholder
import moneysurfer.feature.transaction.generated.resources.transactions_list_sort_newest
import moneysurfer.feature.transaction.generated.resources.transactions_list_sort_oldest
import org.jetbrains.compose.resources.stringResource

/**
 * The design's search field with the filter button inside it. The badge counts applied filters
 * only — the search text is right there in the field, so counting it too would say the same thing
 * twice.
 */
@Composable
internal fun SearchRow(
    query: String,
    activeFilterCount: Int,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    val filtersLabel = stringResource(Res.string.transactions_list_filters_content_description)
    SurferSearchField(
        value = query,
        onValueChange = { onEvent(TransactionsByAccountEvent.OnSearchQueryChanged(it)) },
        placeholder = stringResource(Res.string.transactions_list_search_placeholder),
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        trailing = {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge {
                            Text(
                                stringResource(
                                    Res.string.transactions_list_filters_badge,
                                    activeFilterCount,
                                ),
                            )
                        }
                    }
                },
            ) {
                IconButton(onClick = { onEvent(TransactionsByAccountEvent.OnOpenFiltersClick) }) {
                    Icon(imageVector = SurferIcons.Filter, contentDescription = filtersLabel)
                }
            }
        },
    )
}

/**
 * `Type` / `Account` / `Category` / `Sort`, plus a date chip once an explicit range replaces the
 * pager — without it the range would be applied with nothing on screen saying so.
 *
 * Every chip opens the filter screen rather than cycling its own value: the chips are shortcuts
 * into it, so there is one place where a filter is changed and one place it can be read from.
 */
@Composable
internal fun FilterChipRail(
    chips: TransactionFilterChipsUi,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.dateRangeValue()?.let { value ->
            RailChip(
                label = stringResource(Res.string.transactions_list_chip_date),
                value = value,
                onClick = onOpenFilters,
            )
        }
        RailChip(
            label = stringResource(Res.string.transactions_list_chip_type),
            value = chips.type.chipValue(),
            onClick = onOpenFilters,
        )
        RailChip(
            label = stringResource(Res.string.transactions_list_chip_account),
            value = selectionValue(chips.accountName, chips.accountCount),
            onClick = onOpenFilters,
        )
        RailChip(
            label = stringResource(Res.string.transactions_list_chip_category),
            value = selectionValue(chips.categoryName, chips.categoryCount),
            onClick = onOpenFilters,
        )
        RailChip(
            label = stringResource(Res.string.transactions_list_chip_sort),
            value = chips.sort.chipValue(),
            // The sort chip always spells out its value, so "is it filled" has to come from
            // whether it differs from the default rather than from having a value at all.
            highlighted = chips.sort != TransactionSort.Newest,
            onClick = onOpenFilters,
        )
    }
}

/** A chip reads `Type` when unset and `Type · Expenses` once it carries a value. */
@Composable
private fun RailChip(
    label: String,
    value: String?,
    onClick: () -> Unit,
    highlighted: Boolean = value != null,
) {
    val selected = highlighted
    val text = if (value != null) {
        stringResource(Res.string.transactions_list_chip_value, label, value)
    } else {
        label
    }
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else AppTheme.materialColors.outline,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.labelLarge,
            color = if (selected) {
                AppTheme.materialColors.onSecondaryContainer
            } else {
                AppTheme.materialColors.onSurface
            },
            maxLines = 1,
        )
    }
}

/** One pick shows its name, several show how many — null leaves the chip in its bare state. */
@Composable
private fun selectionValue(name: String?, count: Int): String? = when {
    count == 0 -> null
    name != null -> name
    else -> stringResource(Res.string.transactions_list_chip_selected_count, count)
}

@Composable
private fun TransactionFilterChipsUi.dateRangeValue(): String? = when (val range = dateRange) {
    TransactionDateRange.FollowPeriod -> null
    is TransactionDateRange.Custom -> stringResource(Res.string.transactions_list_chip_date_custom)
    is TransactionDateRange.Preset -> range.preset.chipValue()
}

@Composable
private fun TransactionDatePreset.chipValue(): String = stringResource(
    when (this) {
        TransactionDatePreset.Today -> Res.string.transaction_filters_date_today
        TransactionDatePreset.Yesterday -> Res.string.transaction_filters_date_yesterday
        TransactionDatePreset.ThisWeek -> Res.string.transaction_filters_date_this_week
        TransactionDatePreset.ThisMonth -> Res.string.transaction_filters_date_this_month
        TransactionDatePreset.LastMonth -> Res.string.transaction_filters_date_last_month
        TransactionDatePreset.ThisYear -> Res.string.transaction_filters_date_this_year
    },
)

@Composable
private fun TransactionTypeFilter.chipValue(): String? = when (this) {
    TransactionTypeFilter.All -> null
    TransactionTypeFilter.Expenses -> stringResource(Res.string.transactions_list_filter_expenses)
    TransactionTypeFilter.Income -> stringResource(Res.string.transactions_list_filter_income)
}

@Composable
private fun TransactionSort.chipValue(): String = stringResource(
    when (this) {
        TransactionSort.Newest -> Res.string.transactions_list_sort_newest
        TransactionSort.Oldest -> Res.string.transactions_list_sort_oldest
    },
)
