package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_close_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_datetime_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_datetime_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_save
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun DateTimeField(
    timestamp: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldShape = AppTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(fieldShape)
            .border(1.dp, AppTheme.materialColors.outline, fieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Event,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.transaction_creation_datetime_label),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = timestamp?.let(::formatShortDate)
                    ?: stringResource(Res.string.transaction_creation_datetime_placeholder),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Icon(
            imageVector = SurferIcons.DropDown,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDatePickerDialog(
    initialTimestamp: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val nowYear = remember(now) {
        Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .year
    }
    val notFuture = remember(now, nowYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= now
            override fun isSelectableYear(year: Int): Boolean = year <= nowYear
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialTimestamp,
        selectableDates = notFuture,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onConfirm) },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.transaction_creation_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.transaction_creation_close_content_description))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

private const val MONTH_ABBREVIATION_LENGTH = 3

private fun formatShortDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val ld = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = ld.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(MONTH_ABBREVIATION_LENGTH)
    val day = ld.day.toString().padStart(2, '0')
    return "$day $month ${ld.year}"
}
