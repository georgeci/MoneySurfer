package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** Goal lifecycle as the design system sees it — the domain enum stays in `domain`. */
enum class SurferGoalStatus { Active, Completed, Paused, Archived }

/**
 * Status chip on a goal card and in the details hero. ACTIVE is outlined with a dot,
 * COMPLETED is filled with a check, PAUSED and ARCHIVED are neutral.
 */
@Composable
fun SurferGoalStatusPill(
    status: SurferGoalStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.materialColors
    val filled = status == SurferGoalStatus.Completed
    val content = when (status) {
        SurferGoalStatus.Active -> colors.primary
        SurferGoalStatus.Completed -> colors.onPrimary
        SurferGoalStatus.Paused, SurferGoalStatus.Archived -> colors.onSurfaceVariant
    }
    val base = Modifier
        .clip(CircleShape)
        .then(
            if (filled) {
                Modifier.background(colors.primary)
            } else {
                Modifier.border(1.dp, content.copy(alpha = BORDER_ALPHA), CircleShape)
            },
        )

    Row(
        modifier = modifier.then(base).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (status) {
            SurferGoalStatus.Active -> Dot(color = content)
            SurferGoalStatus.Completed -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )

            SurferGoalStatus.Paused, SurferGoalStatus.Archived -> Unit
        }
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private const val BORDER_ALPHA = 0.5f

@Preview
@Composable
private fun SurferGoalStatusPillPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurferGoalStatusPill(SurferGoalStatus.Active, "Active")
            SurferGoalStatusPill(SurferGoalStatus.Completed, "Reached")
            SurferGoalStatusPill(SurferGoalStatus.Paused, "Paused")
            SurferGoalStatusPill(SurferGoalStatus.Archived, "Archived")
        }
    }
}
