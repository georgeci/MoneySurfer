package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetProgressBar
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatusPill
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * One budget as the dashboard row draws it. Everything numeric arrives pre-formatted — `uikit`
 * knows nothing about `Money`, currencies or the wording of a status.
 */
data class SurferBudgetItem(
    val id: String,
    val name: String,
    /** The status word in the pill — "On track", "Near limit", "Over". */
    val statusLabel: String,
    val status: SurferBudgetStatus,
    /** Spend against the limit, as one line: "€312.40 of €400.00". */
    val spentOfLimit: String,
    /** What is left, or what was overspent — the caller picks the wording from [status]. */
    val remaining: String,
    /** Spend as a fraction of the limit; can exceed 1, which the bar caps rather than overdraws. */
    val progress: Float,
    /** Where the budget's alert threshold sits, drawn as the tick the fill is read against. */
    val alertFraction: Float? = null,
)

/**
 * Budgets widget for the dashboard column: the budgets most worth looking at, each with its spend,
 * its remainder and the bar between them. Ordering is the caller's — the widget renders the list it
 * is handed, and only decides how many of them fit the card.
 *
 * [seeAllTestTag] tags the "see all" link so a UI test can tap it without matching localized copy;
 * the host screen owns the tag value because the link is a step in its flow.
 */
@Composable
fun SurferBudgetsWidget(
    items: List<SurferBudgetItem>,
    title: String,
    seeAllLabel: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferBudgetItem) -> Unit)? = null,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
    seeAllTestTag: String? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val visibleItems = if (hero) items.take(HERO_ROWS) else items.take(COMPACT_ROWS)

    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = {
            Text(
                text = seeAllLabel,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier
                    .clickable(onClick = onSeeAllClick)
                    .then(seeAllTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            )
        },
    ) {
        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Category,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
            return@SurferWidgetCard
        }

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visibleItems.forEach { item ->
                BudgetRow(
                    item = item,
                    onClick = onItemClick?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(item: SurferBudgetItem, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        modifier = rowModifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name,
                style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Fills the space the pill leaves rather than half of it, so a long name
                // ellipsizes only once it genuinely runs out of row.
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            SurferBudgetStatusPill(label = item.statusLabel, status = item.status)
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = item.spentOfLimit,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = item.remaining,
                style = AppTheme.typography.bodySmall,
                color = if (item.status == SurferBudgetStatus.Over) {
                    AppTheme.materialColors.error
                } else {
                    AppTheme.materialColors.onSurfaceVariant
                },
            )
        }

        // No screen-reader line of its own: the row is one merged node, and the bar is a picture of
        // the two figures printed right above it — describing it again would read them twice.
        SurferBudgetProgressBar(
            progress = item.progress,
            status = item.status,
            tickFraction = item.alertFraction,
        )
    }
}

/** Three budgets fill the full-size card; the compact one keeps the single most pressing budget. */
private const val HERO_ROWS = 3
private const val COMPACT_ROWS = 1

@Preview
@Composable
private fun SurferBudgetsWidgetPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBudgetsWidget(
                items = listOf(
                    SurferBudgetItem(
                        id = "1",
                        name = "Transport & fuel",
                        statusLabel = "Over",
                        status = SurferBudgetStatus.Over,
                        spentOfLimit = "€245.10 of €220.00",
                        remaining = "€25.10 over",
                        progress = 1.11f,
                        alertFraction = 0.75f,
                    ),
                    SurferBudgetItem(
                        id = "2",
                        name = "Groceries",
                        statusLabel = "Near limit",
                        status = SurferBudgetStatus.Warn,
                        spentOfLimit = "€312.40 of €400.00",
                        remaining = "€87.60 left",
                        progress = 0.78f,
                        alertFraction = 0.8f,
                    ),
                    SurferBudgetItem(
                        id = "3",
                        name = "Total monthly cap",
                        statusLabel = "On track",
                        status = SurferBudgetStatus.Ok,
                        spentOfLimit = "€1,408.30 of €2,200.00",
                        remaining = "€791.70 left",
                        progress = 0.64f,
                        alertFraction = 0.8f,
                    ),
                ),
                title = "Budgets",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBudgetsWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBudgetsWidget(
                items = emptyList(),
                title = "Budgets",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                emptyTitle = "No budgets yet",
                emptySubtitle = "Set a cap on a category to start tracking.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
