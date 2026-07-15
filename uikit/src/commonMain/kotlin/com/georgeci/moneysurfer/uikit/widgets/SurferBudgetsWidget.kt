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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferBudgetItem(
    val id: String,
    val name: String,
    val spentFormatted: String,
    val limitFormatted: String,
    val remainderFormatted: String,
    val progress: Float,
    val isOver: Boolean,
    val isNear: Boolean,
)

@Composable
fun SurferBudgetsWidget(
    items: List<SurferBudgetItem>,
    title: String,
    seeAllLabel: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferBudgetItem) -> Unit)? = null,
    subtitle: String? = null,
    overLabel: String = "Over",
    nearLabel: String = "Near",
    leftLabelFormat: (String) -> String = { "$it left" },
    overLabelFormat: (String) -> String = { "$it over" },
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
) {
    val hero = size == SurferWidgetSize.Hero
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
                .padding(horizontal = 20.dp, vertical = 0.dp),
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
                text = seeAllLabel,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onSeeAllClick),
            )
        }

        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Wallet,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visibleItems.forEach { item ->
                BudgetRow(
                    item = item,
                    onClick = onItemClick?.let { handler -> { handler(item) } },
                    overLabel = overLabel,
                    nearLabel = nearLabel,
                    leftLabelFormat = leftLabelFormat,
                    overLabelFormat = overLabelFormat,
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(
    item: SurferBudgetItem,
    onClick: (() -> Unit)?,
    overLabel: String,
    nearLabel: String,
    leftLabelFormat: (String) -> String,
    overLabelFormat: (String) -> String,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        modifier = rowModifier.padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name,
                style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            when {
                item.isOver -> StatusPill(
                    label = overLabel,
                    bg = AppTheme.materialColors.errorContainer,
                    fg = AppTheme.materialColors.onErrorContainer,
                )
                item.isNear -> StatusPill(
                    label = nearLabel,
                    bg = AppTheme.materialColors.tertiaryContainer,
                    fg = AppTheme.materialColors.onTertiaryContainer,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${item.spentFormatted} / ${item.limitFormatted}",
                style = AppTheme.typography.labelLarge,
                color = if (item.isOver) {
                    AppTheme.materialColors.error
                } else {
                    AppTheme.materialColors.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(progress = item.progress, isOver = item.isOver)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (item.isOver) {
                overLabelFormat(
                    item.remainderFormatted,
                )
            } else {
                leftLabelFormat(item.remainderFormatted)
            },
            style = AppTheme.typography.bodySmall,
            color = if (item.isOver) {
                AppTheme.materialColors.error
            } else {
                AppTheme.materialColors.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StatusPill(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
private fun ProgressTrack(progress: Float, isOver: Boolean) {
    val track = AppTheme.materialColors.surfaceContainerHigh
    val fill = if (isOver) {
        AppTheme.materialColors.error
    } else {
        AppTheme.materialColors.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        val capped = progress.coerceIn(0f, 1f)
        if (capped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(capped)
                    .height(8.dp)
                    .background(fill),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBudgetsWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBudgetsWidget(
                items = listOf(
                    SurferBudgetItem("1", "Groceries", "€220", "€280", "€60", 0.78f, isOver = false, isNear = false),
                    SurferBudgetItem("2", "Dining", "€162", "€127", "€35", 1.27f, isOver = true, isNear = false),
                    SurferBudgetItem("3", "Leisure", "€95", "€120", "€25", 0.85f, isOver = false, isNear = true),
                ),
                title = "Budgets",
                subtitle = "3 active · 12 days left",
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
