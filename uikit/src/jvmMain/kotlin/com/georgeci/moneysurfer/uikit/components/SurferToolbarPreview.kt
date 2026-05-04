package com.georgeci.moneysurfer.uikit.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarPreview() {
    SurferComponentPreview {
        SurferToolbar(title = "Screen Title")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithBackPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Details",
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithActionsPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Dashboard",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = SurferIcons.Settings,
                        contentDescription = "Settings",
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarFullPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Edit Account",
            onBack = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = SurferIcons.Delete,
                        contentDescription = "Delete",
                    )
                }
            },
        )
    }
}
