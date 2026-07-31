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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** How [SurferAccountStatCard] renders its value. */
enum class SurferStatValueFormat {
    /** Split-amount treatment: superscript currency sign, large integer, small fraction. */
    Amount,

    /**
     * Verbatim, at the same size and weight as [Amount]'s integer part. For values that are not
     * money — counts, percentages, streaks — which the split treatment would pad into a fake
     * amount ("14" → "14.00").
     */
    Plain,
}

/**
 * Compact "in / out / balance · period" stat tile used on Account Details (and other surfaces
 * that need a small KPI). Built on [SurferCard] so it inherits the active container style; the
 * caller picks the leading icon and its tint to denote direction (income, expense, neutral).
 *
 * Pass [value] as a pre-formatted amount string, or set [valueFormat] to
 * [SurferStatValueFormat.Plain] to render it as typed. When unknown, pass `null` to render an
 * em-dash placeholder; an empty string is treated the same way.
 *
 * [label] is kept to one line: tiles are narrow — three across a 411 dp phone leave under 70 dp
 * for text — and a word longer than the tile breaks mid-word rather than wrapping. Keep labels
 * short enough that the ellipsis never shows.
 */
@Composable
fun SurferAccountStatCard(
    label: String,
    value: String?,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    valueFormat: SurferStatValueFormat = SurferStatValueFormat.Amount,
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
                    contentDescription = SurferSemantics.Decorative,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = label,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                displayValue == null -> Text(
                    text = "—",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.materialColors.onSurface,
                )

                valueFormat == SurferStatValueFormat.Plain -> Text(
                    text = displayValue,
                    style = AppTheme.typography.displayLarge.copy(
                        fontSize = SurferSplitAmountTier.Stat.integerSize,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = SurferSplitAmountTier.Stat.letterSpacing,
                        lineHeight = 1.em,
                    ),
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                else -> SurferSplitAmount(
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
private fun SurferAccountStatCardPlainPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferAccountStatCard(
                label = "Avg / mo",
                value = "€196.83",
                icon = SurferIcons.Calendar,
                iconTint = AppTheme.materialColors.primary,
                modifier = Modifier.weight(1f),
            )
            SurferAccountStatCard(
                label = "Txns",
                value = "14",
                icon = SurferIcons.Receipt,
                iconTint = AppTheme.materialColors.primary,
                modifier = Modifier.weight(1f),
                valueFormat = SurferStatValueFormat.Plain,
            )
            SurferAccountStatCard(
                label = "Per txn",
                value = "€12.04",
                icon = SurferIcons.Tag,
                iconTint = AppTheme.materialColors.primary,
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
