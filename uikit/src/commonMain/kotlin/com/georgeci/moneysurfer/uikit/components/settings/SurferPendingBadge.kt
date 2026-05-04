package com.georgeci.moneysurfer.uikit.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** Inline pill used inside `supporting` slots to flag attention items (e.g. "2 pending"). */
@Composable
fun SurferPendingBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.materialColors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.primary),
        )
        Text(
            text = text,
            style = AppTheme.typography.labelMedium,
            color = colors.onPrimaryContainer,
        )
    }
}

@Preview
@Composable
private fun SurferPendingBadgePreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferPendingBadge("2 pending")
            SurferPendingBadge("New")
            SurferPendingBadge("Action required")
        }
    }
}
