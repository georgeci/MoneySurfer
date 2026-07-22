package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** One bar of [SurferCategoryTrendCard]. [value] is a magnitude; [label] is the axis tick. */
data class SurferCategoryTrendBar(
    val label: String,
    val value: Long,
    val formattedValue: String,
)

/**
 * Month-by-month spend for one category, drawn as bar columns in the category's [tint].
 *
 * Bars are scaled against the tallest bar in the set rather than against an absolute ceiling,
 * so a quiet category still reads as a shape instead of a flat line. The final column is the
 * current month and is emphasised — it is the one number the rest exist to give context to.
 *
 * A set that is entirely zero draws every bar at the floor height instead of dividing by zero,
 * which is the honest picture for a category nothing has been booked to yet.
 */
@Composable
fun SurferCategoryTrendCard(
    title: String,
    bars: List<SurferCategoryTrendBar>,
    tint: Color,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    SurferCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (trailingLabel != null) {
                    Text(
                        text = trailingLabel,
                        style = AppTheme.typography.labelMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            val max = bars.maxOfOrNull { it.value } ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth().height(ChartHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEachIndexed { index, bar ->
                    TrendColumn(
                        bar = bar,
                        tint = tint,
                        fraction = if (max > 0L) bar.value.toFloat() / max else 0f,
                        emphasised = index == bars.lastIndex,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendColumn(
    bar: SurferCategoryTrendBar,
    tint: Color,
    fraction: Float,
    emphasised: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (emphasised) {
        AppTheme.materialColors.onSurface
    } else {
        AppTheme.materialColors.onSurfaceVariant
    }
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${bar.label}: ${bar.formattedValue}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Only the current column carries its value — six stacked numerals on a phone-width
        // chart collide, and the other five are readable from the bar heights.
        if (emphasised) {
            Text(
                text = bar.formattedValue,
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight(fraction))
                .clip(AppTheme.shapes.extraSmall)
                .background(if (emphasised) tint else tint.copy(alpha = InactiveBarAlpha))
                .then(
                    if (emphasised) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, tint.copy(alpha = InactiveBorderAlpha), AppTheme.shapes.extraSmall)
                    },
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = bar.label,
            style = AppTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Keeps an empty month visible as a stub rather than collapsing the column to nothing. */
private fun barHeight(fraction: Float) = MinBarHeight + (MaxBarHeight - MinBarHeight) * fraction.coerceIn(0f, 1f)

private val ChartHeight = 132.dp
private val MinBarHeight = 3.dp
private val MaxBarHeight = 92.dp
private const val InactiveBarAlpha = 0.28f
private const val InactiveBorderAlpha = 0.45f

@Preview
@Composable
private fun SurferCategoryTrendCardPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferCategoryTrendCard(
                title = "Last 6 months",
                trailingLabel = "Avg €190",
                tint = SurferCategoryPalette.tints[5],
                bars = listOf(
                    SurferCategoryTrendBar("Nov", 20500, "€205"),
                    SurferCategoryTrendBar("Dec", 19200, "€192"),
                    SurferCategoryTrendBar("Jan", 24000, "€240"),
                    SurferCategoryTrendBar("Feb", 19800, "€198"),
                    SurferCategoryTrendBar("Mar", 17600, "€176"),
                    SurferCategoryTrendBar("Apr", 16855, "€168.55"),
                ),
            )
            SurferCategoryTrendCard(
                title = "Last 6 months",
                tint = SurferCategoryPalette.tints[3],
                bars = List(6) { SurferCategoryTrendBar("M$it", 0, "€0") },
            )
        }
    }
}
