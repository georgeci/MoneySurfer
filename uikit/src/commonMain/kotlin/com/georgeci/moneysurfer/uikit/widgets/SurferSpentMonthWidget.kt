package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Composable
fun SurferSpentMonthWidget(
    title: String,
    spent: String,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    progress: Float = 0f,
    caption: String? = null,
    trailingLabel: String? = null,
    trailingLabelColor: Color? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val clampedProgress = progress.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.materialColors.surfaceContainerHigh)
            .fillMaxSize()
            .padding(if (hero) 20.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (hero) 14.dp else 10.dp),
    ) {
        Text(
            text = title,
            style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Text(
            text = spent,
            style = if (hero) AppTheme.typography.displaySmall else AppTheme.typography.headlineSmall,
            color = AppTheme.materialColors.onSurface,
        )
        ProgressTrack(
            progress = clampedProgress,
            modifier = Modifier.fillMaxWidth(),
        )
        if (caption != null || trailingLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = caption.orEmpty(),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                if (trailingLabel != null) {
                    Text(
                        text = trailingLabel,
                        style = AppTheme.typography.labelSmall,
                        color = trailingLabelColor ?: AppTheme.materialColors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppTheme.materialColors.surfaceContainerHighest),
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppTheme.materialColors.primary),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSpentMonthWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSpentMonthWidget(
                title = "Spent this month",
                spent = "€1,173.69",
                progress = 1173.69f / 1800f,
                caption = "of €1,800 budget",
                trailingLabel = "−18% vs last",
                trailingLabelColor = Color(0xFF1F7A4F),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSpentMonthWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSpentMonthWidget(
                title = "Spent this month",
                spent = "€0.00",
                progress = 0f,
                caption = "No budget set",
                trailingLabel = "Set budget",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}
