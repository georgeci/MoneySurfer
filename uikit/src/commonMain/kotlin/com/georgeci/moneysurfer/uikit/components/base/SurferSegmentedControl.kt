package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * Pill-shaped segmented control for picking exactly one value from a small set of options.
 * Selected segment gets the secondaryContainer fill plus a leading check; segments are split by
 * 1dp outline dividers. Caller renders any section label above the control.
 */
@Composable
fun <T> SurferSegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = AppTheme.materialColors.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(1.dp, outline, RoundedCornerShape(percent = 50)),
    ) {
        options.forEachIndexed { index, value ->
            val isSelected = value == selected
            val bg =
                if (isSelected) AppTheme.materialColors.secondaryContainer else Color.Transparent
            val fg = if (isSelected) {
                AppTheme.materialColors.onSecondaryContainer
            } else {
                AppTheme.materialColors.onSurface
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(bg)
                    .clickable { onSelect(value) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = SurferIcons.Check,
                        // decorative — selection state indicator; the segment label provides the accessible label
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label(value),
                    style = AppTheme.typography.labelLarge,
                    color = fg,
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(outline),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferSegmentedControlPreview() {
    SurferComponentPreview {
        SurferSegmentedControl(
            modifier = Modifier.padding(all = 16.dp),
            options = listOf("Cash", "Bank", "Card", "Savings"),
            selected = "Bank",
            label = { it },
            onSelect = {},
        )
    }
}
