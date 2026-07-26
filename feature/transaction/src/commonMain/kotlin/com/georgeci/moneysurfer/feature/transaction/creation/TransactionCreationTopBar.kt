package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_close_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_save
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_create
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_edit
import moneysurfer.feature.transaction.generated.resources.transaction_creation_update
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_content_description
import org.jetbrains.compose.resources.stringResource

/**
 * Close · title · [delete] · Save.
 *
 * Both words on the bar and the delete action itself hang off edit mode, so they live together
 * here rather than as three separate branches inside the screen body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreationTopBar(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    val title = if (state.isEditMode) {
        stringResource(Res.string.transaction_creation_title_edit)
    } else {
        stringResource(Res.string.transaction_creation_title_create)
    }
    val saveLabel = if (state.isEditMode) {
        stringResource(Res.string.transaction_creation_update)
    } else {
        stringResource(Res.string.transaction_creation_save)
    }
    TopAppBar(
        title = { Text(title, style = AppTheme.typography.titleLarge) },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(48.dp)
                    .testTag(TransactionCreationTestTags.Close)
                    .clickable { onEvent(TransactionCreationEvent.OnBackClick) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Close,
                    contentDescription = stringResource(
                        Res.string.transaction_creation_close_content_description,
                    ),
                )
            }
        },
        actions = {
            // Destructive action sits before Save, never where the commit button was a moment ago
            // on the create screen — a mis-tap here can only be taken back through the snackbar.
            if (state.isEditMode) {
                SurferToolbarAction(
                    icon = SurferIcons.Delete,
                    contentDescription = stringResource(
                        Res.string.transaction_details_delete_content_description,
                    ),
                    tint = AppTheme.materialColors.error,
                    onClick = { onEvent(TransactionCreationEvent.OnDeleteClick) },
                    modifier = Modifier.testTag(TransactionCreationTestTags.Delete),
                )
            }
            SurferToolbarButtonAction(
                icon = SurferIcons.Check,
                text = saveLabel,
                onClick = { onEvent(TransactionCreationEvent.OnSaveClick) },
                enabled = state.isSaveEnabled,
                modifier = Modifier.testTag(TransactionCreationTestTags.Save),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppTheme.materialColors.surface,
        ),
    )
}
