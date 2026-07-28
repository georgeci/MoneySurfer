package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * A single 32dp pill chip with a 10dp corner: when [selected] it fills with `secondaryContainer`
 * (and optionally shows a leading check); otherwise it stays outline-only. The one shared shape
 * behind both the mutually-exclusive [SurferFilterChipRow] and the transactions filter rail, so a
 * tweak to the selected fill or corner lands in one place.
 *
 * [showCheck] draws the leading check on selection — on for a single-select row where the check is
 * the selection cue, off for a value-carrying chip whose fill alone signals it is set.
 */
@Composable
fun SurferSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = true,
) {
    val fg = if (selected) {
        AppTheme.materialColors.onSecondaryContainer
    } else {
        AppTheme.materialColors.onSurface
    }
    Row(
        modifier = modifier
            // Without this, selection is a fill colour and a deliberately decorative check —
            // nothing a screen reader, or a test, can read back. `this.` is load-bearing: the
            // bare name inside `semantics {}` is the parameter, not the property.
            .semantics { this.selected = selected }
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent)
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
        if (selected && showCheck) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = SurferSemantics.Decorative,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = AppTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
        )
    }
}
