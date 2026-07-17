package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

enum class SurferInsightTone { Good, Warn, Neutral }

data class SurferInsightItem(
    val id: String,
    val tone: SurferInsightTone,
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@Composable
fun SurferInsightsWidget(
    items: List<SurferInsightItem>,
    title: String,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferInsightItem) -> Unit)? = null,
    badgeFormat: ((Int) -> String)? = null,
    emptyText: String? = null,
) {
    val hero = size == SurferWidgetSize.Hero
    val visibleItems = if (hero) items.take(3) else items.take(1)

    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = {
            if (items.isNotEmpty() && badgeFormat != null) {
                Text(
                    text = badgeFormat(items.size),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.primary,
                )
            }
        },
    ) {
        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Sparkle,
                title = null,
                subtitle = emptyText,
            )
            return@SurferWidgetCard
        }

        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleItems.forEach { item ->
                InsightCard(
                    item = item,
                    onClick = onItemClick?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

@Composable
private fun InsightCard(item: SurferInsightItem, onClick: (() -> Unit)?) {
    val (bg, fg) = when (item.tone) {
        SurferInsightTone.Good ->
            AppTheme.materialColors.tertiaryContainer to
                AppTheme.materialColors.onTertiaryContainer
        SurferInsightTone.Warn ->
            AppTheme.materialColors.errorContainer to
                AppTheme.materialColors.onErrorContainer
        SurferInsightTone.Neutral ->
            AppTheme.materialColors.surfaceContainerHigh to
                AppTheme.materialColors.onSurface
    }
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                // decorative — insight category icon; the item title provides the accessible label
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = AppTheme.typography.titleSmall,
                color = fg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.body,
                style = AppTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f),
            )
        }
    }
}

@Preview
@Composable
private fun SurferInsightsWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferInsightsWidget(
                items = listOf(
                    SurferInsightItem(
                        id = "1",
                        tone = SurferInsightTone.Warn,
                        icon = SurferIcons.ArrowUp,
                        title = "Dining is up 28%",
                        body = "€162 spent — €35 above your usual €127 / month.",
                    ),
                    SurferInsightItem(
                        id = "2",
                        tone = SurferInsightTone.Good,
                        icon = SurferIcons.Sparkle,
                        title = "On track to save €420",
                        body = "You spent 21% less on Leisure than last month.",
                    ),
                ),
                title = "Insights",
                badgeFormat = { "$it new" },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferInsightsWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferInsightsWidget(
                items = emptyList(),
                title = "Insights",
                emptyText = "Nothing notable this period.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }
    }
}
