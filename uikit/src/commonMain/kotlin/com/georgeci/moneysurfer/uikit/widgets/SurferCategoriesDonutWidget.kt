package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferDonutSegment(
    val label: String,
    val percent: Float,
    val color: Color,
)

@Composable
fun SurferCategoriesDonutWidget(
    segments: List<SurferDonutSegment>,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    centerLabel: String? = null,
    centerValue: String? = null,
    emptyCenterText: String? = null,
    emptyLegendText: String? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val donutSize = if (hero) 140.dp else 84.dp
    val legendItems = if (hero) 5 else 3
    val isEmpty = segments.isEmpty()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.materialColors.surfaceContainerHigh)
            .padding(if (hero) 20.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (hero) 20.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Donut(
            segments = segments,
            hero = hero,
            centerLabel = centerLabel,
            centerValue = centerValue,
            emptyCenterText = emptyCenterText,
            modifier = Modifier.size(donutSize),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (hero) 8.dp else 4.dp),
        ) {
            if (isEmpty) {
                if (emptyLegendText != null) {
                    Text(
                        text = emptyLegendText,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            } else {
                segments.take(legendItems).forEach { seg ->
                    LegendRow(seg = seg, hero = hero)
                }
            }
        }
    }
}

@Composable
private fun Donut(
    segments: List<SurferDonutSegment>,
    hero: Boolean,
    centerLabel: String?,
    centerValue: String?,
    emptyCenterText: String?,
    modifier: Modifier = Modifier,
) {
    val trackColor = AppTheme.materialColors.surfaceContainerHighest
    val innerBg = AppTheme.materialColors.surfaceContainerHigh
    val innerInset = if (hero) 18.dp else 10.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (segments.isEmpty()) {
                drawCircle(color = trackColor)
            } else {
                var startDeg = -90f
                segments.forEach { seg ->
                    val sweep = seg.percent * 360f
                    drawArc(
                        color = seg.color,
                        startAngle = startDeg,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                    startDeg += sweep
                }
            }
            val insetPx = innerInset.toPx()
            drawCircle(
                color = innerBg,
                radius = (size.minDimension / 2f) - insetPx,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (segments.isEmpty()) {
                Text(
                    text = emptyCenterText.orEmpty(),
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                if (centerLabel != null) {
                    Text(
                        text = centerLabel,
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
                if (centerValue != null) {
                    Text(
                        text = centerValue,
                        style = if (hero) AppTheme.typography.titleMedium else AppTheme.typography.titleSmall,
                        color = AppTheme.materialColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(seg: SurferDonutSegment, hero: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(seg.color),
        )
        Text(
            text = seg.label,
            style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(seg.percent * 100).toInt()}%",
            style = if (hero) AppTheme.typography.labelMedium else AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun SurferCategoriesDonutHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferCategoriesDonutWidget(
                segments = listOf(
                    SurferDonutSegment("Rent", 0.45f, Color(0xFF8E6BB8)),
                    SurferDonutSegment("Groceries", 0.22f, Color(0xFF4D9075)),
                    SurferDonutSegment("Dining", 0.12f, Color(0xFFC07856)),
                    SurferDonutSegment("Transport", 0.08f, Color(0xFF5F7DC9)),
                    SurferDonutSegment("Utilities", 0.06f, Color(0xFFB58752)),
                    SurferDonutSegment("Leisure", 0.04f, Color(0xFFC5708F)),
                    SurferDonutSegment("Health", 0.03f, Color(0xFFC46D67)),
                ),
                centerLabel = "Top",
                centerValue = "Rent",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferCategoriesDonutEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferCategoriesDonutWidget(
                segments = emptyList(),
                emptyCenterText = "No data",
                emptyLegendText = "No expenses tracked yet.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}
