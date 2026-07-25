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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Centered empty-state block: an icon medallion, a title, an optional subtitle and an
 * optional call-to-action. Use it when a list or screen has no content yet — the CTA is
 * what turns a dead end into the next step the user should take.
 *
 * [actionTestTag] tags the CTA button so a UI test can tap it without matching localized copy.
 */
@Composable
fun SurferEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = SurferIcons.Info,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionTestTag: String? = null,
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
                .background(AppTheme.materialColors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onSurfaceVariant,
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
        if (actionLabel != null && onActionClick != null) {
            SurferButton(
                text = actionLabel,
                onClick = onActionClick,
                style = SurferButtonStyle.Tonal,
                startIcon = SurferIcons.Add,
                modifier = Modifier
                    .padding(top = AppTheme.spacing.xSmall)
                    .then(actionTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            )
        }
    }
}

@Preview
@Composable
private fun SurferEmptyStatePreview() {
    SurferComponentPreview {
        SurferEmptyState(
            title = "No activity yet",
            subtitle = "Transactions you log will show up here.",
            icon = SurferIcons.Receipt,
            actionLabel = "Add transaction",
            onActionClick = {},
        )
    }
}

@Preview
@Composable
private fun SurferEmptyStateNoActionPreview() {
    SurferComponentPreview {
        SurferEmptyState(
            title = "Nothing scheduled",
            subtitle = "Subscriptions and recurring bills will appear here.",
            icon = SurferIcons.Event,
        )
    }
}
