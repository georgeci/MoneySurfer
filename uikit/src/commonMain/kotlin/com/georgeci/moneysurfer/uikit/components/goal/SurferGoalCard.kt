package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * List card for one savings goal: emoji tile, title, status pill, progress bar and the
 * saved / target line. A PAUSED or ARCHIVED goal is dimmed so a scan of the list reads
 * "these are not moving" without any extra copy.
 */
@Suppress("LongParameterList")
@Composable
fun SurferGoalCard(
    title: String,
    emoji: String,
    hue: Int,
    status: SurferGoalStatus,
    statusLabel: String,
    progress: Float,
    savedLabel: String,
    targetLabel: String,
    percentLabel: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val dimmed = status == SurferGoalStatus.Paused || status == SurferGoalStatus.Archived
    SurferCard(
        modifier = modifier.fillMaxWidth().alpha(if (dimmed) DIMMED_ALPHA else 1f),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
                SurferGoalIcon(emoji = emoji, hue = hue)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = AppTheme.materialColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (caption != null) {
                        Text(
                            text = caption,
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.materialColors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(AppTheme.spacing.small))
                SurferGoalStatusPill(status = status, label = statusLabel)
            }

            SurferGoalProgressBar(progress = progress, color = goalAccentColor(hue))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$savedLabel / $targetLabel",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                Text(
                    text = percentLabel,
                    style = AppTheme.typography.labelLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

private const val DIMMED_ALPHA = 0.7f

@Preview
@Composable
private fun SurferGoalCardPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferGoalCard(
                title = "Emergency fund",
                emoji = "☂️",
                hue = 210,
                status = SurferGoalStatus.Active,
                statusLabel = "Active",
                progress = 0.74f,
                savedLabel = "€8,915",
                targetLabel = "€12,000",
                percentLabel = "74%",
                caption = "by Aug 2026",
            )
            SurferGoalCard(
                title = "Lisbon trip",
                emoji = "🏖️",
                hue = 30,
                status = SurferGoalStatus.Paused,
                statusLabel = "Paused",
                progress = 0.62f,
                savedLabel = "€1,480",
                targetLabel = "€2,400",
                percentLabel = "62%",
            )
        }
    }
}
