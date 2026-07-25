package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Row of mutually-exclusive filter chips. Each chip is a [SurferSelectableChip]: the selected one
 * fills with `secondaryContainer` and shows a leading check, the rest stay outline-only. Caller
 * maps the picked option however it likes — the row is stateless.
 */
@Composable
fun <T> SurferFilterChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        options.forEach { option ->
            SurferSelectableChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Preview
@Composable
private fun SurferFilterChipRowPreview() {
    SurferComponentPreview {
        SurferFilterChipRow(
            modifier = Modifier.padding(all = 16.dp),
            options = listOf("All", "Expenses", "Income"),
            selected = "All",
            label = { it },
            onSelect = {},
        )
    }
}
