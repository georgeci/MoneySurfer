package com.georgeci.moneysurfer.uikit.components.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferOutlinedChip
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

private val IconSize = 48.dp
private val IconCorner = RoundedCornerShape(14.dp)

/**
 * Full workspace selector row. Renders via [SurferCard] — variant + selected fill driven by the
 * theme's container style. When [selected] is true and [showActions] is on, "Edit" and "Members"
 * chips appear under the title.
 */
@Composable
fun SurferWorkspaceRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = false,
    editLabel: String? = null,
    membersLabel: String? = null,
    onEditClick: () -> Unit = {},
    onMembersClick: () -> Unit = {},
) {
    SurferCard(
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick.takeIf { enabled },
    ) {
        RowBody(
            name = name,
            subtitle = subtitle,
            selected = selected,
            showActions = selected && showActions,
            enabled = enabled,
            editLabel = editLabel,
            membersLabel = membersLabel,
            onEditClick = onEditClick,
            onMembersClick = onMembersClick,
        )
    }
}

@Composable
fun SurferCreateWorkspaceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferActionCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize)
                    .clip(IconCorner)
                    .background(AppTheme.materialColors.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Add,
                    contentDescription = SurferSemantics.Decorative,
                    tint = AppTheme.materialColors.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowBody(
    name: String,
    subtitle: String,
    selected: Boolean,
    showActions: Boolean,
    enabled: Boolean,
    editLabel: String?,
    membersLabel: String?,
    onEditClick: () -> Unit,
    onMembersClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WorkspaceIconBubble(name = name)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showActions) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (editLabel != null) {
                        SurferOutlinedChip(
                            label = editLabel,
                            icon = SurferIcons.Edit,
                            onClick = onEditClick,
                            enabled = enabled,
                        )
                    }
                    if (membersLabel != null) {
                        SurferOutlinedChip(
                            label = membersLabel,
                            icon = SurferIcons.People,
                            onClick = onMembersClick,
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        RadioIndicator(selected = selected, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun WorkspaceIconBubble(name: String) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(IconSize)
            .clip(IconCorner)
            .background(AppTheme.materialColors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Composable
private fun RadioIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = AppTheme.materialColors.primary
    val outline = AppTheme.materialColors.outline
    val surface = AppTheme.materialColors.surface
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(primary)
                } else {
                    Modifier.border(2.dp, outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(surface),
            )
        }
    }
}

@Preview
@Composable
private fun SurferWorkspaceRowPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default),
            modifier = Modifier.padding(16.dp),
        ) {
            SurferWorkspaceRow(
                name = "Family",
                subtitle = "Household budget",
                selected = true,
                enabled = true,
                showActions = true,
                editLabel = "Edit",
                membersLabel = "Members",
                onClick = {},
                onEditClick = {},
                onMembersClick = {},
            )
            SurferWorkspaceRow(
                name = "Family",
                subtitle = "Selected without actions",
                selected = true,
                enabled = true,
                onClick = {},
            )
            SurferWorkspaceRow(
                name = "Lisbon trip",
                subtitle = "Apr 12 – Apr 22",
                selected = false,
                enabled = true,
                onClick = {},
            )
            SurferCreateWorkspaceRow(
                title = "New workspace",
                subtitle = "For a trip, side project, or anything",
                onClick = {},
            )
        }
    }
}
