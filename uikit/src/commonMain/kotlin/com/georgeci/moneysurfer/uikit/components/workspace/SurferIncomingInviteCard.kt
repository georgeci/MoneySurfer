package com.georgeci.moneysurfer.uikit.components.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Card for an incoming workspace invite addressed to the current user. Shown on the
 * "Invitations" screen.
 *
 * Layout: a small workspace-icon avatar + workspace name + inviter handle on the top row,
 * with a role chip pinned to the trailing edge. Bottom row carries the action buttons
 * (Decline / Accept) for active invites, or just an "Expired" tag for expired ones — no
 * action buttons in the expired case (a separate cleanup path purges them).
 *
 * i18n-free by contract: callers pass already-resolved strings (workspace name, role label,
 * button labels, expired label). Keeps uikit independent of the feature module's resource
 * bundle.
 */
@Composable
fun SurferIncomingInviteCard(
    workspaceName: String,
    inviterLabel: String,
    roleLabel: String,
    expired: Boolean,
    expiredLabel: String,
    acceptLabel: String,
    declineLabel: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val cs = AppTheme.materialColors
    SurferCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SurferIcons.People,
                        contentDescription = SurferSemantics.Decorative,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = workspaceName,
                        style = AppTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = inviterLabel,
                        style = AppTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoleChip(roleLabel)
            }

            Spacer(Modifier.height(AppTheme.spacing.small))

            if (expired) {
                Text(
                    text = expiredLabel,
                    style = AppTheme.typography.labelMedium,
                    color = cs.error,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
                ) {
                    Spacer(Modifier.weight(1f))
                    SurferButton(
                        text = declineLabel,
                        style = SurferButtonStyle.Text,
                        size = SurferButtonSize.Small,
                        enabled = !busy,
                        onClick = onDecline,
                    )
                    SurferButton(
                        text = acceptLabel,
                        style = SurferButtonStyle.Filled,
                        size = SurferButtonSize.Small,
                        startIcon = SurferIcons.Check,
                        enabled = !busy,
                        onClick = onAccept,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleChip(label: String) {
    val cs = AppTheme.materialColors
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.secondaryContainer)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = cs.onSecondaryContainer,
        )
    }
}

@Preview
@Composable
private fun SurferIncomingInviteCardPreview_Active() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferIncomingInviteCard(
                workspaceName = "Lisbon trip '26",
                inviterLabel = "@kasiam",
                roleLabel = "Editor",
                expired = false,
                expiredLabel = "Expired",
                acceptLabel = "Accept",
                declineLabel = "Decline",
                onAccept = {},
                onDecline = {},
            )
        }
    }
}

@Preview
@Composable
private fun SurferIncomingInviteCardPreview_Expired() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferIncomingInviteCard(
                workspaceName = "Office lunch club with a really long name that ellipses",
                inviterLabel = "@marekw",
                roleLabel = "Viewer",
                expired = true,
                expiredLabel = "Expired",
                acceptLabel = "Accept",
                declineLabel = "Decline",
                onAccept = {},
                onDecline = {},
            )
        }
    }
}

@Preview
@Composable
private fun SurferIncomingInviteCardPreview_Busy() {
    SurferComponentPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferIncomingInviteCard(
                workspaceName = "Family",
                inviterLabel = "@dadj",
                roleLabel = "Editor",
                expired = false,
                expiredLabel = "Expired",
                acceptLabel = "Accept",
                declineLabel = "Decline",
                onAccept = {},
                onDecline = {},
                busy = true,
            )
        }
    }
}
