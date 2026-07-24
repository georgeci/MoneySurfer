package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.atom.SurferIconBubble
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * The "create one instead" row that closes a picker sheet: an outlined plus medallion, a primary
 * coloured label and an optional line of supporting text.
 *
 * The account and category choosers had this written out separately and differed only in whether
 * a [subtitle] was present, so it is nullable rather than a second component.
 */
@Composable
fun SurferAddNewCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SurferActionCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferIconBubble(icon = SurferIcons.Add, outlined = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.primary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SurferAddNewCardPreview() {
    SurferComponentPreview {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
            SurferAddNewCard(
                label = "Add new account",
                subtitle = "Bank, savings, cash or card",
                onClick = {},
            )
            SurferAddNewCard(label = "Create new category", onClick = {})
        }
    }
}
