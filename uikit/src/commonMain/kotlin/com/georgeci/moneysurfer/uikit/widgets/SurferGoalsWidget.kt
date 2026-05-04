package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
) {
    val hero = size == SurferWidgetSize.Hero
    val visibleItems = if (hero) items.take(2) else items.take(1)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.materialColors.surface)
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = seeAllLabel,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onSeeAllClick),
            )
        }

        if (items.isEmpty()) {
            EmptyBox(title = emptyTitle, subtitle = emptySubtitle)
            return@Column
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
        modifier = rowModifier.padding(horizontal = 20.dp),
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

@Composable
private fun ProgressRing(
    progress: Float,
    size: androidx.compose.ui.unit.Dp,
) {
    val track = AppTheme.materialColors.surfaceContainerHigh
    val fill = AppTheme.materialColors.primary
    val percentText = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = (this.size.minDimension / 16f).coerceAtLeast(3f)
            val inset = stroke / 2f
            val box = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke(width = stroke),
            )
            rotate(degrees = -90f) {
                drawArc(
                    color = fill,
                    startAngle = 0f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        Text(
            text = percentText,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Composable
private fun EmptyBox(title: String?, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Savings,
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (title != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

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
