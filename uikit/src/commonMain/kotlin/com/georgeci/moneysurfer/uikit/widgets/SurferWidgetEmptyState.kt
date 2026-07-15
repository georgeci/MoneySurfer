package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Shared empty-state placeholder used by the home widgets: a circular icon badge
 * over an optional title and subtitle, centered inside the widget body.
 *
 * A `null` [title] or [subtitle] is simply omitted, so a widget that only shows a
 * single hint line passes it as [subtitle] with a `null` [title].
 */
@Composable
internal fun SurferWidgetEmptyState(
    icon: ImageVector,
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
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
                imageVector = icon,
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
