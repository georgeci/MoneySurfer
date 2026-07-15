package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Selectable currency row: a symbol badge, the code + display name, and a trailing check
 * when [selected]. Shared by the currency bottom sheet and the first-run currency picker,
 * which differ only in [horizontalPadding] and an optional test tag passed via [modifier].
 */
@Composable
fun SurferCurrencyRow(
    symbol: String,
    code: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    val highlight = AppTheme.materialColors.primary.copy(alpha = 0.10f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) highlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = code,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = name,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = null,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}
