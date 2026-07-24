package com.georgeci.moneysurfer.feature.category.manage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.components.SurferConfirmDialog
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
    SurferConfirmDialog(
        title = stringResource(Res.string.categories_manage_delete_title),
        message = stringResource(Res.string.categories_manage_delete_message, categoryName),
        detail = if (childCount > 0) {
            stringResource(Res.string.categories_manage_delete_children, childCount)
        } else {
            null
        },
        confirmLabel = stringResource(Res.string.categories_manage_delete_confirm),
        cancelLabel = stringResource(Res.string.categories_manage_delete_cancel),
        icon = SurferIcons.Delete,
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
