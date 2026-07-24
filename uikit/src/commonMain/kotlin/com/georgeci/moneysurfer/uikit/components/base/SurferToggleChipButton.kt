package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Compact toolbar button that reads as pressed while [active]: filled-tonal when on, outlined
 * when off. Used for a mode a screen stays in — "Edit" turning into "Done" over a reorderable
 * list — where a plain button would not show that the mode is currently engaged.
 *
 * Caller supplies both the [icon] and [label] for each state, since the two states are usually
 * different words rather than one label with a highlight.
 */
@Composable
fun SurferToggleChipButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            modifier = Modifier.size(if (active) ActiveIconSize else IconSize),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = AppTheme.typography.labelLarge)
    }
    if (active) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = MinHeight),
            contentPadding = ContentPadding,
        ) {
            content()
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = MinHeight),
            contentPadding = ContentPadding,
        ) {
            content()
        }
    }
}

private val MinHeight = 32.dp
private val IconSize = 14.dp
private val ActiveIconSize = 16.dp
private val ContentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)

@Preview
@Composable
private fun SurferToggleChipButtonPreview() {
    SurferComponentPreview {
        Row {
            SurferToggleChipButton(
                label = "Edit",
                icon = SurferIcons.Edit,
                active = false,
                onClick = {},
            )
            Spacer(Modifier.width(AppTheme.spacing.small))
            SurferToggleChipButton(
                label = "Done",
                icon = SurferIcons.Check,
                active = true,
                onClick = {},
            )
        }
    }
}
