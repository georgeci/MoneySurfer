package com.georgeci.moneysurfer.feature.transaction.filters

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_any
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_custom
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_follows_period
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_from
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_last_month
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_picker_clear
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_picker_confirm
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_picker_dismiss
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_month
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_week
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_this_year
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_to
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_today
import moneysurfer.feature.transaction.generated.resources.transaction_filters_date_yesterday
import moneysurfer.feature.transaction.generated.resources.transaction_filters_section_date
import moneysurfer.feature.transaction.generated.resources.transactions_list_date_full
import moneysurfer.feature.transaction.generated.resources.transactions_list_months_genitive
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Preset chips plus explicit From/To fields.
 *
 * The From/To fields are only shown for a custom range: while a preset is picked they would be
 * read-only echoes of it, and while the range follows the period pager there is no range to show
 * — the caption says where the dates are coming from instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateRangeSection(
    dateRange: TransactionDateRange,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterSectionHeader(stringResource(Res.string.transaction_filters_section_date))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionDatePreset.entries.forEach { preset ->
                FilterChip(
                    selected = (dateRange as? TransactionDateRange.Preset)?.preset == preset,
                    onClick = { onEvent(TransactionFiltersEvent.OnDatePresetClick(preset)) },
                    label = { Text(preset.label()) },
                )
            }
            FilterChip(
                selected = dateRange is TransactionDateRange.Custom,
                onClick = { onEvent(TransactionFiltersEvent.OnCustomDateClick) },
                label = { Text(stringResource(Res.string.transaction_filters_date_custom)) },
            )
        }
        when (dateRange) {
            TransactionDateRange.FollowPeriod -> Text(
                text = stringResource(Res.string.transaction_filters_date_follows_period),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            is TransactionDateRange.Custom -> CustomRangeFields(range = dateRange, onEvent = onEvent)
            is TransactionDateRange.Preset -> Unit
        }
    }
}

@Composable
private fun CustomRangeFields(
    range: TransactionDateRange.Custom,
    onEvent: (TransactionFiltersEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(Res.string.transaction_filters_date_from),
            date = range.from,
            onPicked = { onEvent(TransactionFiltersEvent.OnCustomFromPicked(it)) },
            modifier = Modifier.weight(1f),
        )
        DateField(
            label = stringResource(Res.string.transaction_filters_date_to),
            date = range.to,
            onPicked = { onEvent(TransactionFiltersEvent.OnCustomToPicked(it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    onPicked: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val shape = AppTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, AppTheme.materialColors.outline, shape)
            .clickable { picking = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Event,
            // decorative — the field label carries the meaning
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = date?.let { formatFilterDate(it) }
                    ?: stringResource(Res.string.transaction_filters_date_any),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
            )
        }
    }
    if (picking) {
        FilterDatePickerDialog(
            initial = date,
            onDismiss = { picking = false },
            onPicked = {
                picking = false
                onPicked(it)
            },
        )
    }
}

/**
 * Unlike the transaction-entry picker this one allows future dates — a filter over planned
 * transactions is exactly the case where "to the end of next month" is a sensible bound — and it
 * can clear the bound, which is what an open-ended range means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate?) -> Unit,
) {
    // The picker speaks UTC millis, so the conversion has to be UTC on both sides or a date
    // picked east of Greenwich comes back a day early.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPicked(
                        state.selectedDateMillis?.let {
                            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                        },
                    )
                },
            ) { Text(stringResource(Res.string.transaction_filters_date_picker_confirm)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPicked(null) }) {
                    Text(stringResource(Res.string.transaction_filters_date_picker_clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.transaction_filters_date_picker_dismiss))
                }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Genitive month, as everywhere else a concrete date is spelled out on these screens. */
@Composable
private fun formatFilterDate(date: LocalDate): String = stringResource(
    Res.string.transactions_list_date_full,
    date.day,
    stringArrayResource(Res.array.transactions_list_months_genitive)[date.month.number - 1],
    date.year,
)

@Composable
private fun TransactionDatePreset.label(): String = stringResource(
    when (this) {
        TransactionDatePreset.Today -> Res.string.transaction_filters_date_today
        TransactionDatePreset.Yesterday -> Res.string.transaction_filters_date_yesterday
        TransactionDatePreset.ThisWeek -> Res.string.transaction_filters_date_this_week
        TransactionDatePreset.ThisMonth -> Res.string.transaction_filters_date_this_month
        TransactionDatePreset.LastMonth -> Res.string.transaction_filters_date_last_month
        TransactionDatePreset.ThisYear -> Res.string.transaction_filters_date_this_year
    },
)
