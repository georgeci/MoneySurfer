package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * One subcategory in the parent's breakdown: bubble, name over a share bar, amount and share.
 *
 * [share] is the fraction of the parent's total this child accounts for, already computed by the
 * caller — the row draws one child and has no view of its siblings. Values outside `0..1` are
 * clamped rather than trusted, so a rounding artefact cannot paint a bar past the track.
 */
@Composable
fun SurferCategoryBreakdownRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    formattedAmount: String,
    sharePercentLabel: String,
    share: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            SurferCategoryBubble(icon = icon, tint = tint, size = 36.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TrackHeight)
                        .clip(AppTheme.shapes.extraSmall)
                        .background(tint.copy(alpha = TrackAlpha)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(share.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(AppTheme.shapes.extraSmall)
                            .background(tint),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = sharePercentLabel,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

private val TrackHeight = 4.dp
private const val TrackAlpha = 0.20f

@Preview
@Composable
private fun SurferCategoryBreakdownRowPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferCategoryBreakdownRow(
                icon = SurferIcons.Receipt,
                tint = SurferCategoryPalette.tints[5],
                name = "Delivery",
                formattedAmount = "€106.15",
                sharePercentLabel = "63%",
                share = 0.63f,
            )
            SurferCategoryBreakdownRow(
                icon = SurferIcons.Cash,
                tint = SurferCategoryPalette.tints[5],
                name = "Coffee",
                formattedAmount = "€62.40",
                sharePercentLabel = "37%",
                share = 0.37f,
            )
        }
    }
}
