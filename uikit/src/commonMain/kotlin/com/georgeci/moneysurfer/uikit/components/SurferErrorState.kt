package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Centered error-state block: an error-tinted icon medallion, a title, an optional subtitle
 * and an optional retry action. Mirrors [SurferEmptyState] but signals a failed load rather
 * than absent content — use [SurferEmptyState] when the load succeeded but produced nothing.
 *
 * @param onRetry when non-null, renders a retry button; override [retryLabel] to relabel it.
 */
@Composable
fun SurferErrorState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = SurferIcons.Info,
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                // decorative — error illustration; the title/subtitle convey the message
                contentDescription = null,
                tint = AppTheme.materialColors.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            SurferButton(
                text = retryLabel,
                onClick = onRetry,
                style = SurferButtonStyle.Tonal,
                modifier = Modifier.padding(top = AppTheme.spacing.xSmall),
            )
        }
    }
}

@Preview
@Composable
private fun SurferErrorStatePreview() {
    SurferComponentPreview {
        SurferErrorState(
            title = "Something went wrong",
            subtitle = "We couldn't load your accounts. Check your connection and try again.",
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SurferErrorStateNoRetryPreview() {
    SurferComponentPreview {
        SurferErrorState(title = "Failed to sync")
    }
}
