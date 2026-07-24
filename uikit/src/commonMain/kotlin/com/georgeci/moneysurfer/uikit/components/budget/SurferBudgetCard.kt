package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** One category's mark on a budget card. */
data class SurferBudgetCategoryVisual(
    val icon: ImageVector,
    val tint: Color,
)

/**
 * The pre-formatted numbers on a budget card. Grouped so the card stays a slot component with a
 * short parameter list — uikit knows nothing about `Money` or currencies, only these strings.
 */
data class SurferBudgetCardMetrics(
    val spentOfLimit: String,
    val remaining: String,
    val progress: Float,
    val footer: String,
    val alertFraction: Float? = null,
)

/**
 * A budget row on the list screen. Everything numeric arrives pre-formatted via [metrics].
 */
@Composable
fun SurferBudgetCard(
    name: String,
    statusLabel: String,
    status: SurferBudgetStatus,
    metrics: SurferBudgetCardMetrics,
    modifier: Modifier = Modifier,
    categories: List<SurferBudgetCategoryVisual> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (categories.isNotEmpty()) {
                    SurferStackedCategoryBubbles(categories)
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = name,
                    style = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                SurferBudgetStatusPill(label = statusLabel, status = status)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metrics.spentOfLimit,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = metrics.remaining,
                    style = AppTheme.typography.bodySmall,
                    color = if (status == SurferBudgetStatus.Over) {
                        AppTheme.materialColors.error
                    } else {
                        AppTheme.materialColors.onSurfaceVariant
                    },
                )
            }

            SurferBudgetProgressBar(
                progress = metrics.progress,
                status = status,
                alertFraction = metrics.alertFraction,
            )

            Text(
                text = metrics.footer,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Up to [MAX_BUBBLES] overlapping category bubbles, then a `+N` counter. Overlapping keeps a
 * five-category budget the same width as a one-category budget, so the rows stay aligned.
 */
@Composable
private fun SurferStackedCategoryBubbles(categories: List<SurferBudgetCategoryVisual>) {
    val visible = categories.take(MAX_BUBBLES)
    val overflow = categories.size - visible.size
    val border = AppTheme.materialColors.surface
    Row(horizontalArrangement = Arrangement.spacedBy(BUBBLE_OVERLAP)) {
        visible.forEach { visual ->
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .clip(CircleShape)
                    .background(visual.tint.copy(alpha = 0.18f))
                    .border(1.5.dp, border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = SurferSemantics.Decorative,
                    tint = visual.tint,
                    modifier = Modifier.size(BUBBLE_SIZE / 2),
                )
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.surfaceContainerHighest)
                    .border(1.5.dp, border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.offset(y = (-1).dp),
                )
            }
        }
    }
}

private const val MAX_BUBBLES = 3
private val BUBBLE_SIZE = 28.dp

/** Negative spacing — each bubble sits partly on top of the one before it. */
private val BUBBLE_OVERLAP = (-8).dp

@Preview
@Composable
private fun SurferBudgetCardPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferBudgetCard(
                name = "Groceries",
                statusLabel = "Near limit",
                status = SurferBudgetStatus.Warn,
                metrics = SurferBudgetCardMetrics(
                    spentOfLimit = "€312.40 of €400.00",
                    remaining = "€87.60 left",
                    progress = 0.78f,
                    footer = "Monthly · Apr 1 – Apr 30 · 12 days left",
                    alertFraction = 0.8f,
                ),
                categories = listOf(
                    SurferBudgetCategoryVisual(SurferIcons.Receipt, SurferCategoryPalette.tints[0]),
                ),
                onClick = {},
            )
            SurferBudgetCard(
                name = "Transport & fuel",
                statusLabel = "Over",
                status = SurferBudgetStatus.Over,
                metrics = SurferBudgetCardMetrics(
                    spentOfLimit = "€245.10 of €220.00",
                    remaining = "€25.10 over",
                    progress = 1.11f,
                    footer = "Monthly · Apr 1 – Apr 30 · 12 days left",
                    alertFraction = 0.75f,
                ),
                categories = listOf(
                    SurferBudgetCategoryVisual(SurferIcons.Cash, SurferCategoryPalette.tints[1]),
                    SurferBudgetCategoryVisual(SurferIcons.Wallet, SurferCategoryPalette.tints[2]),
                    SurferBudgetCategoryVisual(SurferIcons.Tag, SurferCategoryPalette.tints[3]),
                    SurferBudgetCategoryVisual(SurferIcons.Event, SurferCategoryPalette.tints[4]),
                ),
                onClick = {},
            )
            SurferBudgetCard(
                name = "Total monthly cap",
                statusLabel = "On track",
                status = SurferBudgetStatus.Ok,
                metrics = SurferBudgetCardMetrics(
                    spentOfLimit = "€1,408.30 of €2,200.00",
                    remaining = "€791.70 left",
                    progress = 0.64f,
                    footer = "Monthly · all categories · 12 days left",
                    alertFraction = 0.8f,
                ),
                onClick = {},
            )
        }
    }
}
