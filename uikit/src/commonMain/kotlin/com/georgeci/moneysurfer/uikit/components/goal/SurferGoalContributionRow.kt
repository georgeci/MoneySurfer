package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * One line of a goal's contribution history. A withdrawal is a contribution with a negative
 * amount, so it differs only in its icon and amount colour.
 *
 * [automatic] styles a row created by a scheduled auto-contribution. Nothing produces those in
 * v1 (autopay is deferred), but the styling belongs with the component rather than with the
 * feature that will later switch it on.
 */
@Suppress("LongParameterList")
@Composable
fun SurferGoalContributionRow(
    amountLabel: String,
    dateLabel: String,
    isWithdrawal: Boolean,
    modifier: Modifier = Modifier,
    note: String? = null,
    automatic: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.materialColors
    val accent = if (isWithdrawal) colors.error else AppTheme.semanticColors.income
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = ICON_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    automatic -> Icons.Default.Autorenew
                    isWithdrawal -> Icons.Default.ArrowUpward
                    else -> Icons.Default.ArrowDownward
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note?.takeIf { it.isNotBlank() } ?: dateLabel,
                style = AppTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrBlank()) {
                Text(
                    text = dateLabel,
                    style = AppTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = amountLabel,
            style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = accent,
        )
    }
}

private const val ICON_BACKGROUND_ALPHA = 0.12f

@Preview
@Composable
private fun SurferGoalContributionRowPreview() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferGoalContributionRow(
                amountLabel = "+€250",
                dateLabel = "12 Jul 2026",
                isWithdrawal = false,
                note = "Salary top-up",
            )
            SurferGoalContributionRow(
                amountLabel = "−€80",
                dateLabel = "3 Jul 2026",
                isWithdrawal = true,
            )
            SurferGoalContributionRow(
                amountLabel = "+€100",
                dateLabel = "1 Jul 2026",
                isWithdrawal = false,
                automatic = true,
                note = "Monthly autopay",
            )
        }
    }
}
