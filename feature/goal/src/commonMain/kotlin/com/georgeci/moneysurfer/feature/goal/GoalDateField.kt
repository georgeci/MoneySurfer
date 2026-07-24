package com.georgeci.moneysurfer.feature.goal

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_edit_cancel
import moneysurfer.feature.goal.generated.resources.goal_edit_save
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val FIELD_MIN_HEIGHT = 56.dp
private val FIELD_VERTICAL_PADDING = 14.dp
private val FIELD_ICON_SIZE = 20.dp

/**
 * Read-only date row that opens a picker — the goal editor's deadline and the contribution
 * date look and behave the same, so they share one field.
 */
@Composable
internal fun GoalDateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val shape = AppTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FIELD_MIN_HEIGHT)
            .clip(shape)
            .border(1.dp, AppTheme.materialColors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.default, vertical = FIELD_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Icon(
            imageVector = SurferIcons.Event,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(FIELD_ICON_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * Date picker for the goal screens. Unlike the transaction picker, future dates are
 * selectable — a goal deadline is by definition ahead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDayIn(zone)?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date)
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.goal_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.goal_edit_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
