package com.georgeci.moneysurfer.uikit.components.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Pending invite row: dashed-circle bell avatar, email, status line (pending / expired with a
 * colored dot), and an outlined role chip. Trailing area is reserved for caller-supplied
 * actions like "more" or "resend / cancel".
 */
@Composable
fun SurferInvitePendingRow(
    email: String,
    statusLabel: String,
    isExpired: Boolean,
    roleLabel: String,
    modifier: Modifier = Modifier,
) {
    val statusDotColor = if (isExpired) {
        AppTheme.materialColors.error
    } else {
        AppTheme.materialColors.tertiary
    }
    val outline = AppTheme.materialColors.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHighest)
                .drawBehind {
                    drawRoundRect(
                        color = outline,
                        style = Stroke(width = 1.dp.toPx()),
                        cornerRadius = CornerRadius(size.minDimension / 2f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Mail,
                // decorative — invite-status avatar; the email address provides the accessible label
                contentDescription = null,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = email,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor),
                )
                Text(
                    text = statusLabel,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        InvitePendingRoleChip(roleLabel)
    }
}

@Composable
private fun InvitePendingRoleChip(label: String) {
    val outlineVariant = AppTheme.materialColors.outlineVariant
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .drawBehind {
                drawRoundRect(
                    color = outlineVariant,
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SurferInvitePendingRowPreview() {
    SurferComponentPreview {
        Column {
            SurferInvitePendingRow(
                email = "kasia.nowak@gmail.com",
                statusLabel = "Pending · expires in 5 days",
                isExpired = false,
                roleLabel = "Editor",
            )
            SurferInvitePendingRow(
                email = "lena@mail.com",
                statusLabel = "Expired",
                isExpired = true,
                roleLabel = "Viewer",
            )
        }
    }
}
