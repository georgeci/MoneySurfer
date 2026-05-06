package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.line.AreaBaseline
import io.github.koalaplot.core.line.AreaPlot2
import io.github.koalaplot.core.style.AreaStyle
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.AxisContent
import io.github.koalaplot.core.xygraph.AxisStyle
import io.github.koalaplot.core.xygraph.GridStyle
import io.github.koalaplot.core.xygraph.Point
import io.github.koalaplot.core.xygraph.TickPosition
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberFloatLinearAxisModel

/**
 * Mini "balance over period" chart tile used on Account Details. Header has a label on the
 * leading edge plus an optional delta on the trailing edge; the sparkline below bleeds to the
 * card edges. Shape mirrors the active container style; elevation only applies under the
 * `Card` style — under `Filled` / `Outlined` the tile sits flat to match siblings.
 */
@Composable
fun SurferBalanceChartCard(
    title: String,
    delta: String?,
    modifier: Modifier = Modifier,
    deltaColor: Color? = null,
    chartColor: Color = AppTheme.materialColors.primary,
    points: List<Pair<Float, Float>> = DefaultBalanceChartPoints,
) {
    val elevated = AppTheme.containerStyle == SurferContainerStyle.Card
    val resolvedDeltaColor = deltaColor ?: AppTheme.semanticColors.income
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.surfaceContainer,
        ),
        elevation = if (elevated) {
            CardDefaults.elevatedCardElevation()
        } else {
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
                disabledElevation = 0.dp,
            )
        },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.labelLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                if (delta != null) {
                    Text(
                        text = delta,
                        style = AppTheme.typography.labelMedium,
                        color = resolvedDeltaColor,
                    )
                }
            }
            Sparkline(
                color = chartColor,
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun Sparkline(
    color: Color,
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val data = remember(points) { points.map { Point(it.first, it.second) } }
    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val xRange = xs.min()..xs.max()
    val yMin = ys.min()
    val yMax = ys.max()
    val padding = ((yMax - yMin).takeIf { it > 0f } ?: 1f) * YPaddingFraction
    val yRange = (yMin - padding)..(yMax + padding)

    val transparentAxis = AxisContent<Float>(
        labels = {},
        title = {},
        style = AxisStyle(
            color = Color.Transparent,
            majorTickSize = 0.dp,
            minorTickSize = 0.dp,
            tickPosition = TickPosition.None,
            lineWidth = 0.dp,
        ),
    )
    val emptyGrid = GridStyle(
        horizontalMajorStyle = null,
        horizontalMinorStyle = null,
        verticalMajorStyle = null,
        verticalMinorStyle = null,
    )

    XYGraph(
        xAxisModel = rememberFloatLinearAxisModel(xRange),
        yAxisModel = rememberFloatLinearAxisModel(yRange),
        xAxisContent = transparentAxis,
        yAxisContent = transparentAxis,
        modifier = modifier,
        gridStyle = emptyGrid,
    ) {
        val lastPoint = data.last()
        AreaPlot2(
            data = data,
            areaBaseline = AreaBaseline.HorizontalLine(yRange.start),
            areaStyle = AreaStyle(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f)),
                ),
            ),
            lineStyle = LineStyle(brush = SolidColor(color), strokeWidth = 2.dp),
            symbol = { point ->
                if (point.x == lastPoint.x && point.y == lastPoint.y) {
                    Symbol(
                        size = 8.dp,
                        shape = CircleShape,
                        fillBrush = SolidColor(color),
                    )
                }
            },
        )
    }
}

private const val YPaddingFraction = 0.1f

internal val DefaultBalanceChartPoints: List<Pair<Float, Float>> = listOf(
    0f to 0.75f,
    0.07f to 0.68f,
    0.14f to 0.78f,
    0.21f to 0.65f,
    0.28f to 0.55f,
    0.36f to 0.62f,
    0.43f to 0.50f,
    0.50f to 0.38f,
    0.57f to 0.52f,
    0.64f to 0.44f,
    0.72f to 0.35f,
    0.79f to 0.28f,
    0.86f to 0.25f,
    0.93f to 0.32f,
    1.0f to 0.30f,
)

@Preview
@Composable
private fun SurferBalanceChartCardPreview() {
    SurferComponentPreview {
        SurferBalanceChartCard(
            modifier = Modifier.padding(16.dp),
            title = "Balance · 30 days",
            delta = "+€412",
        )
    }
}
