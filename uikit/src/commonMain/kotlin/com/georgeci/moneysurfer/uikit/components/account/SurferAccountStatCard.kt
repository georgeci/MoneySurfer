package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Compact "in / out / balance · period" stat tile used on Account Details (and other surfaces
 * that need a small KPI). Built on [SurferCard] so it inherits the active container style; the
 * caller picks the leading icon and its tint to denote direction (income, expense, neutral).
 *
 * Pass [value] as a pre-formatted amount string. When unknown, pass `null` to render an em-dash
 * placeholder; an empty string is treated the same way.
 */
@Composable
fun SurferAccountStatCard(
    label: String,
    value: String?,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val displayValue = value?.takeIf { it.isNotBlank() }
    SurferCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    // decorative — stat-type indicator; the label text provides the accessible name
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = label,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
            if (displayValue == null) {
                Text(
                    text = "—",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.materialColors.onSurface,
                )
            } else {
                SurferSplitAmount(
                    formattedAmount = displayValue,
                    tier = SurferSplitAmountTier.Stat,
                    color = AppTheme.materialColors.onSurface,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferAccountStatCardPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferAccountStatCard(
                label = "In this month",
                value = "€3,200.00",
                icon = SurferIcons.ArrowDown,
                iconTint = Color(0xFF2E9A6A),
                modifier = Modifier.weight(1f),
            )
            SurferAccountStatCard(
                label = "Out this month",
                value = "€1,148.49",
                icon = SurferIcons.ArrowUp,
                iconTint = AppTheme.materialColors.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview
@Composable
private fun SurferAccountStatCardEmptyPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferAccountStatCard(
                label = "In this month",
                value = null,
                icon = SurferIcons.ArrowDown,
                iconTint = Color(0xFF2E9A6A),
                modifier = Modifier.weight(1f),
            )
            SurferAccountStatCard(
                label = "Out this month",
                value = null,
                icon = SurferIcons.ArrowUp,
                iconTint = AppTheme.materialColors.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
