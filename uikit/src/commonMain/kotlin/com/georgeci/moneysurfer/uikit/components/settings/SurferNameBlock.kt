package com.georgeci.moneysurfer.uikit.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Hero card with avatar initial, name, email, and an optional trailing chevron.
 * Used at the top of the Settings hub.
 */
@Composable
fun SurferNameBlock(
    name: String,
    modifier: Modifier = Modifier,
    email: String? = null,
    initial: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { SurferSettingsChevron(tint = AppTheme.materialColors.onPrimaryContainer) },
) {
    val colors = AppTheme.materialColors
    val shape = RoundedCornerShape(20.dp)
    val computedInitial = initial ?: name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = shape)
            .clip(shape)
            .background(colors.primaryContainer)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = computedInitial,
                style = AppTheme.typography.titleLarge,
                color = colors.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = AppTheme.typography.titleMedium,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (email != null) {
                Text(
                    text = email,
                    style = AppTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Preview
@Composable
private fun SurferNameBlockPreview() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferNameBlock(
                name = "Kasia Nowak",
                email = "kasia@georgeci.com",
                onClick = {},
            )
        }
    }
}
