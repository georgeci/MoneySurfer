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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalRingArc
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferGoalItem(
    val id: String,
    val name: String,
    val savedFormatted: String,
    val targetFormatted: String,
    val progress: Float,
    val captionLine: String,
)

/**
 * Goals widget for the dashboard column.
 *
 * [seeAllTestTag] tags the "see all" link so a UI test can tap it without matching localized
 * copy; the host screen owns the tag value because the link is a step in its flow.
 */
@Composable
fun SurferGoalsWidget(
    items: List<SurferGoalItem>,
    title: String,
    seeAllLabel: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferGoalItem) -> Unit)? = null,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
    seeAllTestTag: String? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val visibleItems = if (hero) items.take(2) else items.take(1)

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
                icon = SurferIcons.Savings,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
            return@SurferWidgetCard
        }

        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visibleItems.forEach { item ->
                GoalRow(
                    item = item,
                    onClick = onItemClick?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

@Composable
private fun GoalRow(item: SurferGoalItem, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressRing(progress = item.progress, size = 56.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${item.savedFormatted} / ${item.targetFormatted}",
                    style = AppTheme.typography.labelLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.captionLine,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The compact dashboard variant of the details-screen hero: same arc, percent only.
 * The arc itself lives in `components/goal` so the two rings cannot drift apart.
 */
@Composable
private fun ProgressRing(
    progress: Float,
    size: Dp,
) {
    val percentText = "${(progress.coerceIn(0f, 1f) * PERCENT_SCALE).toInt()}%"
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        SurferGoalRingArc(progress = progress, size = size)
        Text(
            text = percentText,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

private const val PERCENT_SCALE = 100

@Preview
@Composable
private fun SurferGoalsWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferGoalsWidget(
                items = listOf(
                    SurferGoalItem(
                        id = "1",
                        name = "Emergency fund",
                        savedFormatted = "€8,915",
                        targetFormatted = "€12,000",
                        progress = 0.74f,
                        captionLine = "€3,085 left · €770/mo to hit Aug 2026",
                    ),
                    SurferGoalItem(
                        id = "2",
                        name = "Lisbon trip",
                        savedFormatted = "€1,480",
                        targetFormatted = "€2,400",
                        progress = 0.62f,
                        captionLine = "€920 left · €230/mo to hit Sep 2026",
                    ),
                ),
                title = "Goals",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferGoalsWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferGoalsWidget(
                items = emptyList(),
                title = "Goals",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                emptyTitle = "No goals yet",
                emptySubtitle = "Set a savings target to track progress here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
