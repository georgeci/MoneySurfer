package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Row of mutually-exclusive filter chips. Each chip is a [SurferSelectableChip]: the selected one
 * fills with `secondaryContainer` and shows a leading check, the rest stay outline-only. Caller
 * maps the picked option however it likes — the row is stateless.
 *
 * [optionTestTag] tags each chip individually. The row-level [modifier] cannot do that, and
 * without a per-chip tag an E2E driver has nothing to aim at but the chip's localized label —
 * which is exactly the coupling issue #352 removes.
 */
@Composable
fun <T> SurferFilterChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTag: ((T) -> String)? = null,
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
                modifier = optionTestTag?.let { Modifier.testTag(it(option)) } ?: Modifier,
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
