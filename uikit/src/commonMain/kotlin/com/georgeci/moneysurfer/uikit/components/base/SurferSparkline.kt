package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
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
 * A bare trend curve: line, gradient fill down to the floor of the plot, and a dot on the newest
 * point. No axes, no grid, no labels — a sparkline states a shape, and the figures it is a shape of
 * are printed by whatever card it sits in.
 *
 * Draws nothing below two points: one point is a dot with no trend, and the y-range collapses.
 *
 * The y-range is padded by [YPaddingFraction] of the spread so the extremes do not sit on the
 * curve's own bounding box, and a flat series still draws a line through the middle rather than
 * along the edge.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun SurferSparkline(
    points: List<Pair<Float, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val data = remember(points) { points.map { Point(it.first, it.second) } }
    val xMin = points.minOf { it.first }
    val xMax = points.maxOf { it.first }
    val xRange = xMin..xMax
    val yMin = points.minOf { it.second }
    val yMax = points.maxOf { it.second }
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
                if (point === lastPoint) {
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

/**
 * A series of plain values as the evenly-spaced points [SurferSparkline] draws, oldest first.
 *
 * The x axis of a sparkline carries no information — the points are one per period and the periods
 * are equal — so callers holding a bare list of balances do not have to invent one.
 */
fun sparklinePoints(values: List<Float>): List<Pair<Float, Float>> =
    values.mapIndexed { index, value -> index.toFloat() to value }

private const val YPaddingFraction = 0.1f

@Preview
@Composable
private fun SurferSparklinePreview() {
    SurferComponentPreview {
        SurferSparkline(
            points = sparklinePoints(listOf(9_800f, 10_240f, 9_950f, 10_610f, 11_160f, 11_575f)),
            color = AppTheme.materialColors.primary,
            modifier = Modifier.height(72.dp).fillMaxWidth(),
        )
    }
}
