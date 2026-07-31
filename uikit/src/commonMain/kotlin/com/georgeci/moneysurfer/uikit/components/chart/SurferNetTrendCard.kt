package com.georgeci.moneysurfer.uikit.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * One month of [SurferNetTrendCard]. [income] and [expense] are magnitudes, so the taller bar is the
 * bigger number whichever way the month went.
 *
 * [contentDescription] is the whole column as a screen reader should read it — the card draws no
 * per-bar numerals, so this is the only place the figures are spoken.
 */
data class SurferNetTrendColumn(
    val label: String,
    val income: Long,
    val expense: Long,
    val contentDescription: String,
)

/**
 * Income against expense, month by month, as paired bar columns.
 *
 * Built on [SurferBarColumns] — the same treatment the category trend uses — rather than on a
 * charting library, so the app's one chart dependency stays confined to `SurferBalanceChartCard`.
 * Both series share one scale, which is the whole point of the card: two independently scaled rows
 * would make every month look break-even.
 *
 * The last column is the one the selection is anchored in and is drawn solid; the earlier months sit
 * behind an outline. Values are not printed on the bars — twelve numerals over six columns is
 * unreadable at phone width — so the legend and [SurferNetTrendColumn.contentDescription] carry the
 * naming instead.
 */
@Composable
fun SurferNetTrendCard(
    title: String,
    columns: List<SurferNetTrendColumn>,
    incomeLabel: String,
    expenseLabel: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    val incomeTint = AppTheme.semanticColors.income
    val expenseTint = AppTheme.semanticColors.expense
    SurferCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferChartCardHeader(title = title, trailingLabel = trailingLabel)

            Spacer(Modifier.height(ChartHeaderSpacing))

            SurferBarColumns(
                columns = columns.mapIndexed { index, column ->
                    SurferBarColumn(
                        label = column.label,
                        bars = listOf(
                            SurferBar(value = column.income, tint = incomeTint),
                            SurferBar(value = column.expense, tint = expenseTint),
                        ),
                        contentDescription = column.contentDescription,
                        emphasised = index == columns.lastIndex,
                    )
                },
            )

            Spacer(Modifier.height(LegendSpacing))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendEntry(label = incomeLabel, tint = incomeTint)
                LegendEntry(label = expenseLabel, tint = expenseTint)
            }
        }
    }
}

@Composable
private fun LegendEntry(label: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A plain painted Box: it publishes no semantics of its own, so the label beside it is the
        // only thing a screen reader reads — which is what names the series.
        Box(
            modifier = Modifier
                .size(LegendDotSize)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private val LegendDotSize = 10.dp
private val LegendSpacing = 12.dp

@Preview
@Composable
private fun SurferNetTrendCardPreview() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferNetTrendCard(
                title = "Income vs expense",
                trailingLabel = "Last 6 months",
                incomeLabel = "Income",
                expenseLabel = "Expense",
                columns = listOf(
                    SurferNetTrendColumn("Nov", 320_000, 285_000, "Nov: income €3,200, expense €2,850"),
                    SurferNetTrendColumn("Dec", 320_000, 410_000, "Dec: income €3,200, expense €4,100"),
                    SurferNetTrendColumn("Jan", 340_000, 260_000, "Jan: income €3,400, expense €2,600"),
                    SurferNetTrendColumn("Feb", 340_000, 275_000, "Feb: income €3,400, expense €2,750"),
                    SurferNetTrendColumn("Mar", 0, 190_000, "Mar: income €0, expense €1,900"),
                    SurferNetTrendColumn("Apr", 340_000, 301_500, "Apr: income €3,400, expense €3,015"),
                ),
            )
        }
    }
}
