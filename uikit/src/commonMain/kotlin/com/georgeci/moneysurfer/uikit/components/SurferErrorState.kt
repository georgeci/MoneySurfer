package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Centered placeholder for a screen or section that failed to load. Mirrors [SurferEmptyState]
 * but tints the icon with the theme error colour and defaults the action to a retry CTA.
 * Use [SurferEmptyState] when the load succeeded but produced nothing.
 *
 * Fills the available space and centers its content; constrain it with [modifier] (e.g. a fixed
 * height) when embedding inside a section rather than a full screen.
 *
 * @param onRetry when non-null, renders a "Retry" button; override [retryLabel] to relabel it.
 */
@Composable
fun SurferErrorState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector = SurferIcons.Info,
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.materialColors.error,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    text = body,
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
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferErrorStatePreview() {
    SurferComponentPreview {
        SurferErrorState(
            title = "Something went wrong",
            body = "We couldn't load your accounts. Check your connection and try again.",
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SurferErrorStateMinimalPreview() {
    SurferComponentPreview {
        SurferErrorState(title = "Failed to sync")
    }
}
