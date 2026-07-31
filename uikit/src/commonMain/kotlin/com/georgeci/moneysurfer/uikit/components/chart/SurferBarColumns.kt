package com.georgeci.moneysurfer.uikit.components.chart

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
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** One bar inside a [SurferBarColumn]: a magnitude and the tint it is drawn in. */
data class SurferBar(
    val value: Long,
    val tint: Color,
)

/**
 * One column of [SurferBarColumns]: its axis tick, the bars drawn side by side inside it, and
 * whether it is the column the others exist to give context to.
 *
 * [contentDescription] is what a screen reader says for the whole column — the bars themselves are
 * shapes with no text of their own, so the caller has to spell the column out once, in its own copy
 * and its own currency.
 *
 * [valueLabel] prints above the column. Leave it null on every column a phone-width chart would
 * stack numerals in; the bar heights already carry the comparison.
 */
data class SurferBarColumn(
    val label: String,
    val bars: List<SurferBar>,
    val contentDescription: String,
    val valueLabel: String? = null,
    val emphasised: Boolean = false,
)

/**
 * Bar columns scaled against the tallest bar in the set — the treatment `SurferCategoryTrendCard`
 * was built with, generalised to any number of bars per column so an income-vs-expense chart can
 * reuse it instead of reaching for a charting library. KoalaPlot stays confined to
 * `SurferBalanceChartCard`, the one line/area component that needs it.
 *
 * Scaling against the set's own maximum rather than an absolute ceiling is what lets a quiet
 * category (or a quiet half-year) still read as a shape instead of a flat line. A set that is
 * entirely zero draws every bar at the floor height instead of dividing by zero, which is the honest
 * picture for a period nothing was booked in.
 *
 * A column's bars are drawn solid when it is [SurferBarColumn.emphasised] and tinted back behind an
 * outline when it is not, so emphasis survives at any number of bars per column.
 */
@Composable
fun SurferBarColumns(
    columns: List<SurferBarColumn>,
    modifier: Modifier = Modifier,
) {
    val max = columns.maxOfOrNull { column -> column.bars.maxOfOrNull { it.value } ?: 0L } ?: 0L
    Row(
        modifier = modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(ColumnSpacing),
        verticalAlignment = Alignment.Bottom,
    ) {
        columns.forEach { column ->
            BarColumn(
                column = column,
                max = max,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun BarColumn(
    column: SurferBarColumn,
    max: Long,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (column.emphasised) {
        AppTheme.materialColors.onSurface
    } else {
        AppTheme.materialColors.onSurfaceVariant
    }
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = column.contentDescription
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (column.valueLabel != null) {
            Text(
                text = column.valueLabel,
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(ValueSpacing))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BarSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            column.bars.forEach { bar ->
                Bar(
                    bar = bar,
                    fraction = if (max > 0L) bar.value.toFloat() / max else 0f,
                    emphasised = column.emphasised,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(LabelSpacing))
        Text(
            text = column.label,
            style = AppTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = if (column.emphasised) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun Bar(
    bar: SurferBar,
    fraction: Float,
    emphasised: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(barHeight(fraction))
            .clip(AppTheme.shapes.extraSmall)
            .background(if (emphasised) bar.tint else bar.tint.copy(alpha = InactiveBarAlpha))
            .then(
                if (emphasised) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = bar.tint.copy(alpha = InactiveBorderAlpha),
                        shape = AppTheme.shapes.extraSmall,
                    )
                },
            ),
    )
}

/**
 * Header every chart card shares: the title, and an optional caption pinned to its baseline —
 * an average, a total, whatever one number frames the shape below it.
 */
@Composable
internal fun SurferChartCardHeader(
    title: String,
    trailingLabel: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
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
}

/** Keeps an empty column visible as a stub rather than collapsing it to nothing. */
private fun barHeight(fraction: Float) =
    MinBarHeight + (MaxBarHeight - MinBarHeight) * fraction.coerceIn(0f, 1f)

/** Gap between the header and the columns — shared so two chart cards cannot drift apart. */
internal val ChartHeaderSpacing = 14.dp

private val ChartHeight = 132.dp
private val MinBarHeight = 3.dp
private val MaxBarHeight = 92.dp
private val ColumnSpacing = 8.dp
private val BarSpacing = 3.dp
private val LabelSpacing = 6.dp
private val ValueSpacing = 4.dp
private const val InactiveBarAlpha = 0.28f
private const val InactiveBorderAlpha = 0.45f

@Preview
@Composable
private fun SurferBarColumnsPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SurferBarColumns(
                columns = listOf(20_500L, 19_200L, 24_000L, 16_855L).mapIndexed { index, value ->
                    SurferBarColumn(
                        label = "M$index",
                        bars = listOf(SurferBar(value, AppTheme.semanticColors.expense)),
                        contentDescription = "M$index: $value",
                        emphasised = index == 3,
                    )
                },
            )
            SurferBarColumns(
                columns = List(4) { index ->
                    SurferBarColumn(
                        label = "M$index",
                        bars = listOf(
                            SurferBar(30_000L, AppTheme.semanticColors.income),
                            SurferBar(12_000L * (index + 1), AppTheme.semanticColors.expense),
                        ),
                        contentDescription = "M$index",
                        emphasised = index == 3,
                    )
                },
            )
        }
    }
}
