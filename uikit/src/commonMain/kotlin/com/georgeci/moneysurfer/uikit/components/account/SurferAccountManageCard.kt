package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Active-account row for the manage-accounts screen. A single line: tonal icon tile, name +
 * subtitle, then the formatted balance and an optional [trailing] slot. The slot is used to
 * surface a drag handle while the screen is in edit mode; archive / delete affordances live
 * outside the card and are revealed by the surrounding swipe gesture.
 */
@Composable
fun SurferAccountManageCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    formattedBalance: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountIconTile(icon = icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formattedBalance,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

/**
 * Tonal icon tile used in the identity row of [SurferAccountManageCard]. Public so feature code
 * can reuse the same swatch in archived rows or one-off layouts.
 */
@Composable
fun AccountIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val bg = if (muted) {
        AppTheme.materialColors.surfaceContainerHigh
    } else {
        AppTheme.materialColors.primaryContainer
    }
    val fg = if (muted) {
        AppTheme.materialColors.onSurfaceVariant
    } else {
        AppTheme.materialColors.onPrimaryContainer
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Preview
@Composable
private fun SurferAccountManageCardViewPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferAccountManageCard(
                name = "Everyday",
                subtitle = "Bank · •• 1234",
                icon = SurferIcons.Bank,
                formattedBalance = "€2,480.32",
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SurferAccountManageCardEditingPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferAccountManageCard(
                name = "Emergency Fund",
                subtitle = "Savings",
                icon = SurferIcons.Savings,
                formattedBalance = "€8,915.00",
                trailing = {
                    Box(modifier = Modifier.size(width = 24.dp, height = 32.dp))
                },
            )
        }
    }
}
