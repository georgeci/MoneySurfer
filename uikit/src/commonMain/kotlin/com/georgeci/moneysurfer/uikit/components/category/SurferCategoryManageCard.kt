package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Category row for the manage-categories screen: palette bubble, name, the type on the right and
 * a chevron into the detail screen.
 *
 * The counterpart to `SurferAccountManageCard`, which was already here — the category half had
 * stayed behind in the feature, so the two rows drifted apart despite sitting in the same kind of
 * list. Nesting is expressed by the caller's leading padding, since the list arrives in tree
 * order and the indent is all the hierarchy needs.
 */
@Composable
fun SurferCategoryManageCard(
    name: String,
    typeLabel: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = AppTheme.spacing.default,
                vertical = AppTheme.spacing.medium,
            ),
        ) {
            SurferCategoryBubble(
                icon = icon,
                tint = tint,
                size = 36.dp,
                modifier = Modifier.padding(end = AppTheme.spacing.medium),
            )
            Text(
                text = name,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = typeLabel,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Icon(
                imageVector = SurferIcons.ChevronRight,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(start = AppTheme.spacing.small),
            )
        }
    }
}

@Preview
@Composable
private fun SurferCategoryManageCardPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(AppTheme.spacing.default)) {
            SurferCategoryManageCard(
                name = "Groceries",
                typeLabel = "Expense",
                icon = SurferCategoryPalette.icons[3],
                tint = SurferCategoryPalette.tints[3],
                onClick = {},
            )
        }
    }
}
