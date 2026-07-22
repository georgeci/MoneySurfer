package com.georgeci.moneysurfer.feature.category.manage

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.categories_manage_delete_cancel
import moneysurfer.feature.category.generated.resources.categories_manage_delete_children
import moneysurfer.feature.category.generated.resources.categories_manage_delete_confirm
import moneysurfer.feature.category.generated.resources.categories_manage_delete_message
import moneysurfer.feature.category.generated.resources.categories_manage_delete_title
import org.jetbrains.compose.resources.stringResource

/**
 * [childCount] > 0 adds a line saying the children survive and move up to the root — deleting a
 * parent silently rearranging part of the tree is exactly the sort of thing a confirmation
 * dialog exists to say out loud.
 */
@Composable
fun DeleteCategoryDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    childCount: Int = 0,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = SurferIcons.Delete,
                contentDescription = null,
                tint = AppTheme.materialColors.error,
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.categories_manage_delete_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.categories_manage_delete_message, categoryName),
                    textAlign = TextAlign.Center,
                )
                if (childCount > 0) {
                    Spacer(Modifier.height(AppTheme.spacing.small))
                    Text(
                        text = stringResource(Res.string.categories_manage_delete_children, childCount),
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
            ) {
                Text(stringResource(Res.string.categories_manage_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.categories_manage_delete_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun DeleteCategoryDialogPreview() {
    AppTheme {
        DeleteCategoryDialog(
            categoryName = "Food",
            onConfirm = {},
            onDismiss = {},
            childCount = 2,
        )
    }
}
