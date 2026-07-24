package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Caption above a form control or a group of them ("Type", "Colour", "Opening balance").
 *
 * Every creation and edit screen had grown its own private copy of this, and they had drifted
 * apart — `labelLarge` in most, `titleSmall` in the goal editor, `labelMedium` in the category
 * sheet. This is the majority style; the two outliers now match it.
 *
 * For a header over a *list* section, with an optional trailing hint, use [SurferSectionHeader].
 */
@Composable
fun SurferSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AppTheme.typography.labelLarge,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SurferSectionLabelPreview() {
    SurferComponentPreview {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large)) {
            SurferSectionLabel(text = "Account type")
            SurferSectionLabel(text = "Colour")
        }
    }
}
