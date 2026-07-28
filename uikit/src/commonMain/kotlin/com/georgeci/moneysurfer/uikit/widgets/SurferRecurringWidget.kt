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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferRecurringItem(
    val id: String,
    val name: String,
    val dueLabel: String,
    val amountFormatted: String,
    val isImminent: Boolean = false,
    val icon: ImageVector = SurferIcons.Calendar,
)

@Composable
fun SurferRecurringWidget(
    items: List<SurferRecurringItem>,
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferRecurringItem) -> Unit)? = null,
    subtitle: String? = null,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val visibleItems = if (hero) items.take(3) else items.take(2)

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = actionLabel,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }

        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Calendar,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        visibleItems.forEach { item ->
            RecurringRow(
                item = item,
                onClick = onItemClick?.let { handler -> { handler(item) } },
            )
        }
    }
}

@Composable
private fun RecurringRow(item: SurferRecurringItem, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .background(
                if (item.isImminent) {
                    AppTheme.materialColors.surfaceContainerLow
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.materialColors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.dueLabel,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
        Text(
            text = item.amountFormatted,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Preview
@Composable
private fun SurferRecurringWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferRecurringWidget(
                items = listOf(
                    SurferRecurringItem("1", "Spotify", "Tomorrow", "€9.99", isImminent = true),
                    SurferRecurringItem("2", "Rent", "Apr 30", "€980"),
                    SurferRecurringItem("3", "Gym", "May 02", "€32"),
                ),
                title = "Upcoming",
                subtitle = "€1,021.99 due in next 7 days",
                actionLabel = "Manage",
                onActionClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferRecurringWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferRecurringWidget(
                items = emptyList(),
                title = "Upcoming",
                actionLabel = "Manage",
                onActionClick = {},
                emptyTitle = "Nothing scheduled",
                emptySubtitle = "Subscriptions and recurring bills will appear here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
