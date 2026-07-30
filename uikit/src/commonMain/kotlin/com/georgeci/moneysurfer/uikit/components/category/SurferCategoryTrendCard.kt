package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.chart.ChartHeaderSpacing
import com.georgeci.moneysurfer.uikit.components.chart.SurferBar
import com.georgeci.moneysurfer.uikit.components.chart.SurferBarColumn
import com.georgeci.moneysurfer.uikit.components.chart.SurferBarColumns
import com.georgeci.moneysurfer.uikit.components.chart.SurferChartCardHeader
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview

/** One bar of [SurferCategoryTrendCard]. [value] is a magnitude; [label] is the axis tick. */
data class SurferCategoryTrendBar(
    val label: String,
    val value: Long,
    val formattedValue: String,
)

/**
 * Month-by-month spend for one category, drawn as bar columns in the category's [tint].
 *
 * Bars are scaled against the tallest bar in the set rather than against an absolute ceiling, so a
 * quiet category still reads as a shape instead of a flat line — see [SurferBarColumns], which owns
 * that treatment and the zero-set floor along with it. The final column is the current month and is
 * emphasised: it is the one number the rest exist to give context to.
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
            SurferChartCardHeader(title = title, trailingLabel = trailingLabel)

            Spacer(Modifier.height(ChartHeaderSpacing))

            SurferBarColumns(
                columns = bars.mapIndexed { index, bar ->
                    val emphasised = index == bars.lastIndex
                    SurferBarColumn(
                        label = bar.label,
                        bars = listOf(SurferBar(value = bar.value, tint = tint)),
                        contentDescription = "${bar.label}: ${bar.formattedValue}",
                        // Only the current column carries its value — six stacked numerals on a
                        // phone-width chart collide, and the other five are readable from the bar
                        // heights.
                        valueLabel = bar.formattedValue.takeIf { emphasised },
                        emphasised = emphasised,
                    )
                },
            )
        }
    }
}

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
