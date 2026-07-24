package com.georgeci.moneysurfer.uikit.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Selection dot for a row that is already clickable as a whole.
 *
 * Purely a visual indicator: it takes no click of its own and carries no semantics, because the
 * row around it owns both. Where the control itself must be focusable, use Material's
 * `RadioButton`.
 */
@Composable
fun SurferSelectionRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(OuterSize)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) {
                    AppTheme.materialColors.primary
                } else {
                    AppTheme.materialColors.outline
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(InnerSize)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primary),
            )
        }
    }
}

private val OuterSize = 22.dp
private val InnerSize = 10.dp

@Preview
@Composable
private fun SurferSelectionRadioPreview() {
    SurferComponentPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
            SurferSelectionRadio(selected = true)
            SurferSelectionRadio(selected = false)
        }
    }
}
