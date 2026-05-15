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
 * Centered placeholder for a screen or section that has loaded successfully but has no content
 * to show (empty list, no search results, nothing scheduled). For a failure use [SurferErrorState];
 * while data is still loading use [SurferShimmerBox].
 *
 * Fills the available space and centers its content; constrain it with [modifier] (e.g. a fixed
 * height) when embedding inside a section rather than a full screen.
 *
 * @param action optional call-to-action rendered below the body (e.g. "Add account").
 */
@Composable
fun SurferEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector = SurferIcons.Info,
    action: SurferStateAction? = null,
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
                tint = AppTheme.materialColors.onSurfaceVariant,
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
            if (action != null) {
                SurferButton(
                    text = action.label,
                    onClick = action.onClick,
                    style = SurferButtonStyle.Tonal,
                )
            }
        }
    }
}

/** Label + handler for the optional CTA in [SurferEmptyState]. */
data class SurferStateAction(
    val label: String,
    val onClick: () -> Unit,
)

@Preview
@Composable
private fun SurferEmptyStatePreview() {
    SurferComponentPreview {
        SurferEmptyState(
            title = "No transactions yet",
            body = "Add your first transaction to start tracking your spending.",
            icon = SurferIcons.Receipt,
            action = SurferStateAction(label = "Add transaction", onClick = {}),
        )
    }
}

@Preview
@Composable
private fun SurferEmptyStateMinimalPreview() {
    SurferComponentPreview {
        SurferEmptyState(title = "No results found")
    }
}
