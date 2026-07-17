package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Row of mutually-exclusive filter chips. Each chip is a 32dp pill with a 10dp corner; the
 * selected one fills with `secondaryContainer` and shows a leading check, the rest stay
 * outline-only. Caller maps the picked option however it likes — the row is stateless.
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
            SurferFilterChip(
                selected = option == selected,
                label = label(option),
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun SurferFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val bg = if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent
    val fg = if (selected) {
        AppTheme.materialColors.onSecondaryContainer
    } else {
        AppTheme.materialColors.onSurface
    }
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else AppTheme.materialColors.outline,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                // decorative — selection state indicator; the chip label provides the accessible label
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = AppTheme.typography.labelLarge,
            color = fg,
        )
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
