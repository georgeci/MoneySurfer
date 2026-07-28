package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatusPill
import com.georgeci.moneysurfer.uikit.components.budget.color
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * One bar of the burn-rate chart, already labelled by the caller — uikit does not know how a date
 * is written in the reader's language.
 *
 * [fraction] is the day's spend against the tallest day of the window, so the chart shows the shape
 * of the week rather than the week against a budget. [isToday] marks the last, still-unfinished
 * day; it is drawn solid while the days behind it are tinted back.
 */
data class SurferBurnRateBar(
    val label: String,
    val fraction: Float,
    val isToday: Boolean = false,
)

/**
 * The verdict pill over the projection: a word and the colour that carries it. Null on the widget
 * when nothing caps the month — an estimate with no cap to miss is not off pace, it is just an
 * estimate.
 */
data class SurferBurnRatePace(
    val label: String,
    val status: SurferBudgetStatus,
)

/**
 * The burn-rate card's contents, formatted and localized by the caller.
 *
 * [average] is the headline — what a day has been costing — with [caption] naming the window under
 * it. [projection] is the callout: where the month lands at that rate.
 */
data class SurferBurnRateData(
    val average: String,
    val caption: String,
    val bars: List<SurferBurnRateBar>,
    val projection: String,
    val pace: SurferBurnRatePace? = null,
    /** What a screen reader gets for the chart, which otherwise reads as a row of bare numbers. */
    val chartContentDescription: String? = null,
)

/** The "nothing to chart yet" half of the card. Mirrors [SurferSafeToSpendEmpty], minus the link. */
data class SurferBurnRateEmpty(
    val title: String? = null,
    val subtitle: String? = null,
)

/**
 * Burn-rate widget for the dashboard column: a week of daily spend as bars, the average under the
 * heading, and where the month ends up at that pace.
 *
 * A null [data] draws [empty] — the card keeps its heading rather than disappearing, the same
 * contract [SurferSafeToSpendWidget] follows.
 */
@Composable
fun SurferBurnRateWidget(
    title: String,
    data: SurferBurnRateData?,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    empty: SurferBurnRateEmpty = SurferBurnRateEmpty(),
) {
    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = { data?.pace?.let { SurferBudgetStatusPill(label = it.label, status = it.status) } },
    ) {
        if (data == null) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Clock,
                title = empty.title,
                subtitle = empty.subtitle,
            )
            return@SurferWidgetCard
        }
        BurnRateBody(data = data, sizing = burnRateSizing(size == SurferWidgetSize.Expanded))
    }
}

/**
 * Everything the Compact/Hero switch changes, resolved in one place so the body below reads as a
 * layout instead of a column of size ternaries.
 */
private data class BurnRateSizing(
    val topPadding: Dp,
    val gap: Dp,
    val averageStyle: TextStyle,
    val chartHeight: Dp,
    /**
     * Compact drops the window caption rather than shrinking every line: the average, the bars and
     * the projection are what the widget is for, and the window is seven days either way.
     */
    val showsCaption: Boolean,
)

@Composable
private fun burnRateSizing(hero: Boolean): BurnRateSizing = if (hero) {
    BurnRateSizing(
        topPadding = 10.dp,
        gap = 10.dp,
        averageStyle = AppTheme.typography.displaySmall,
        chartHeight = 64.dp,
        showsCaption = true,
    )
} else {
    BurnRateSizing(
        topPadding = 6.dp,
        gap = 6.dp,
        averageStyle = AppTheme.typography.headlineSmall,
        chartHeight = 40.dp,
        showsCaption = false,
    )
}

@Composable
private fun BurnRateBody(data: SurferBurnRateData, sizing: BurnRateSizing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = sizing.topPadding),
        verticalArrangement = Arrangement.spacedBy(sizing.gap),
    ) {
        Text(
            text = data.average,
            style = sizing.averageStyle,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sizing.showsCaption) {
            Text(
                text = data.caption,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BurnRateChart(
            bars = data.bars,
            // The pill already carries the verdict as a word; the bars take its colour so the two
            // cannot disagree, and fall back to the neutral accent when nothing caps the month.
            barColor = data.pace?.status?.color ?: AppTheme.materialColors.primary,
            chartHeight = sizing.chartHeight,
            contentDescription = data.chartContentDescription,
        )
        Text(
            text = data.projection,
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The bars, plus a label strip under them.
 *
 * Heights are measured off a fixed [chartHeight] rather than laid out with weights: a day with no
 * spend has a zero fraction, and `Modifier.weight(0f)` is not a legal weight. A zero day still draws
 * [BAR_MIN_HEIGHT] of track, so the chart reads as "nothing that day" rather than as a gap in it.
 *
 * The whole chart takes one [contentDescription]: bar by bar it would announce seven bare numbers,
 * and the sentence the caller passes says what they are.
 */
@Composable
private fun BurnRateChart(
    bars: List<SurferBurnRateBar>,
    barColor: Color,
    chartHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight(bar.fraction, chartHeight))
                            .clip(BAR_SHAPE)
                            .background(
                                if (bar.isToday) barColor else barColor.copy(alpha = PAST_DAY_ALPHA),
                            ),
                    )
                }
                Text(
                    text = bar.label,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun barHeight(fraction: Float, chartHeight: Dp): Dp {
    val scaled = chartHeight * fraction.coerceIn(0f, 1f)
    return if (scaled < BAR_MIN_HEIGHT) BAR_MIN_HEIGHT else scaled
}

/**
 * Bar geometry, not a card token: the theme's smallest shape is 12dp, which on a 3dp sliver reads as
 * a lozenge rather than a column. 4dp is the radius `SurferSpentMonthWidget` already gives its
 * progress track, for the same reason.
 */
private val BAR_SHAPE = RoundedCornerShape(4.dp)

private val BAR_GAP = 6.dp

/** A day with nothing booked still draws a sliver, so the chart never looks like it lost a column. */
private val BAR_MIN_HEIGHT = 3.dp

/** The finished days step back so the still-running one reads as the current bar. */
private const val PAST_DAY_ALPHA = 0.45f

private const val PREVIEW_TITLE = "Burn rate"

private val previewBars = listOf(
    SurferBurnRateBar(label = "22", fraction = 0.42f),
    SurferBurnRateBar(label = "23", fraction = 0.18f),
    SurferBurnRateBar(label = "24", fraction = 1f),
    SurferBurnRateBar(label = "25", fraction = 0f),
    SurferBurnRateBar(label = "26", fraction = 0.63f),
    SurferBurnRateBar(label = "27", fraction = 0.31f),
    SurferBurnRateBar(label = "28", fraction = 0.24f, isToday = true),
)

private val previewData = SurferBurnRateData(
    average = "€42.10 a day",
    caption = "over the last 7 days",
    bars = previewBars,
    projection = "€1,263 projected by month end",
    pace = SurferBurnRatePace(label = "On track", status = SurferBudgetStatus.Ok),
)

@Preview
@Composable
private fun SurferBurnRateWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBurnRateWidget(
                title = PREVIEW_TITLE,
                data = previewData,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBurnRateWidgetCompactPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBurnRateWidget(
                title = PREVIEW_TITLE,
                data = previewData,
                size = SurferWidgetSize.Compact,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBurnRateWidgetOffPacePreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBurnRateWidget(
                title = PREVIEW_TITLE,
                data = previewData.copy(
                    average = "€78.40 a day",
                    projection = "€2,352 projected by month end",
                    pace = SurferBurnRatePace(label = "Off pace", status = SurferBudgetStatus.Over),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBurnRateWidgetNoBudgetPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBurnRateWidget(
                title = PREVIEW_TITLE,
                data = previewData.copy(pace = null),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBurnRateWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBurnRateWidget(
                title = PREVIEW_TITLE,
                data = null,
                empty = SurferBurnRateEmpty(
                    title = "Nothing to chart yet",
                    subtitle = "Log a few expenses to see your pace.",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
