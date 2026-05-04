package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Preview
@Composable
private fun SurferBottomSheetContentTitleOnlyPreview() {
    SurferComponentPreview {
        SurferBottomSheetContent(title = "From account")
    }
}

@Preview
@Composable
private fun SurferBottomSheetContentWithSubtitlePreview() {
    SurferComponentPreview {
        SurferBottomSheetContent(
            title = "From account",
            subtitle = "Total across 3 accounts · €11,575.32",
        )
    }
}

@Preview
@Composable
private fun SurferBottomSheetContentFullPreview() {
    SurferComponentPreview {
        SurferBottomSheetContent(
            title = "Delete transaction?",
            subtitle = "“Lidl — weekly shop” will be removed permanently. This can't be undone.",
            button = {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete")
                }
            },
        ) {
            Text(
                text = "Optional body content goes here.",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurface,
            )
        }
    }
}
