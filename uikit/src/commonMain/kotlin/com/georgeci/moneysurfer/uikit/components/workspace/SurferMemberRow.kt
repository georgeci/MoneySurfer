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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * A single member row for the Members list: colored initial avatar, name/role pair, and an
 * outlined role chip at the trailing edge. Tint of the avatar is a stable hash of the initial.
 *
 * When [isYou] is true a small "you" tag sits next to the name; when [secondaryLine] is given
 * (typically the email) it replaces the role-label subtitle while the chip on the trailing edge
 * still surfaces the role.
 */
@Composable
fun SurferMemberRow(
    name: String,
    initial: String,
    roleLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isYou: Boolean = false,
    secondaryLine: String? = null,
) {
    SurferCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(memberTint(initial)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = AppTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name,
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.materialColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isYou) {
                        YouTag()
                    }
                }
                Text(
                    text = secondaryLine ?: roleLabel,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RoleChip(label = roleLabel)
        }
    }
}

@Composable
private fun YouTag() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppTheme.materialColors.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = "you",
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleChip(label: String) {
    val outlineVariant = AppTheme.materialColors.outlineVariant
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .drawBehind {
                drawRoundRect(
                    color = outlineVariant,
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Icon(
            imageVector = SurferIcons.DropDown,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

private val MemberHues = listOf(
    Color(0xFF9B76B5),
    Color(0xFF5F7DC9),
    Color(0xFF4D9075),
    Color(0xFFC07856),
    Color(0xFFC5708F),
    Color(0xFF7B9556),
)

private fun memberTint(initial: String): Color {
    if (initial.isEmpty()) return MemberHues[0]
    val hash = initial.fold(0) { acc, c -> acc * 31 + c.code }
    val idx = ((hash % MemberHues.size) + MemberHues.size) % MemberHues.size
    return MemberHues[idx]
}

@Preview
@Composable
private fun SurferMemberRowPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferMemberRow(name = "Kasia M.", initial = "K", roleLabel = "Owner", isYou = true)
            SurferMemberRow(name = "Julian P.", initial = "J", roleLabel = "Editor")
            SurferMemberRow(name = "Asia W.", initial = "A", roleLabel = "Editor")
            SurferMemberRow(name = "Tom N.", initial = "T", roleLabel = "Viewer")
        }
    }
}
