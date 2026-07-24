package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Confirmation dialog: a tinted glyph, a centred title and message, and a cancel/confirm pair.
 *
 * [destructive] switches the icon and the confirm button to the error palette. That is the whole
 * difference between the delete and archive dialogs that used to be written out separately in
 * each feature, which is why it is a flag rather than two components.
 *
 * [detail] is an optional second line under the message, for a consequence the user should be
 * told about before confirming — deleting a parent category re-parenting its children, say.
 */
@Composable
fun SurferConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    detail: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = SurferSemantics.Decorative,
                tint = if (destructive) {
                    AppTheme.materialColors.error
                } else {
                    AppTheme.materialColors.primary
                },
            )
        },
        title = {
            Text(text = title, textAlign = TextAlign.Center)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = message, textAlign = TextAlign.Center)
                if (detail != null) {
                    Spacer(Modifier.height(AppTheme.spacing.small))
                    Text(
                        text = detail,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = AppTheme.materialColors.error,
                        contentColor = AppTheme.materialColors.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

@Preview
@Composable
private fun SurferConfirmDialogDestructivePreview() {
    AppTheme {
        SurferConfirmDialog(
            title = "Delete account?",
            message = "\"Everyday\" and its transactions will be removed.",
            detail = "2 subcategories will move up to the top level.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
            icon = SurferIcons.Delete,
            destructive = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun SurferConfirmDialogNeutralPreview() {
    AppTheme {
        SurferConfirmDialog(
            title = "Archive account?",
            message = "\"Everyday\" will be hidden from the list. You can restore it later.",
            confirmLabel = "Archive",
            cancelLabel = "Cancel",
            icon = SurferIcons.Archive,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
