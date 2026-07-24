package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Tile representation of an archived account. Built on the dashed-outline [SurferActionCard]
 * atom, with a muted icon tile and a primary-colored "Restore" outlined button. The component
 * pins its own text colors so it isn't tinted primary by the action-card's content color.
 */
@Composable
fun SurferArchivedAccountCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    restoreLabel: String,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferActionCard(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalContentColor provides AppTheme.materialColors.onSurface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountIconTile(icon = icon, muted = true)
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
                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = SurferIcons.Restore,
                        contentDescription = SurferSemantics.Decorative,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = restoreLabel,
                        style = AppTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SurferArchivedAccountCardPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferArchivedAccountCard(
                name = "Revolut (old)",
                subtitle = "Bank · •• 0093 · Archived Nov 2024",
                icon = SurferIcons.CreditCard,
                restoreLabel = "Restore",
                onRestoreClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}
