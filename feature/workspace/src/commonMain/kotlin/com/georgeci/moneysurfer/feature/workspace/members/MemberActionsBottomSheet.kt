package com.georgeci.moneysurfer.feature.workspace.members

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_confirm_remove_cancel
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_error_cannot_demote_owner
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_error_not_owner
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_error_unknown
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Bottom-sheet entry composable for the per-member actions sheet (role picker + Remove). The
 * surrounding [androidx.compose.material3.ModalBottomSheet] is provided by
 * [com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy]. Visual layout lives
 * in [MemberActionsContent] — this composable just maps the ViewModel's state and effects.
 */
@Composable
fun MemberActionsBottomSheet(
    workspaceId: WorkspaceId,
    targetUserId: UserId,
    onDismiss: () -> Unit,
    viewModel: MemberActionsViewModel = koinViewModel(
        key = "${workspaceId.value}:${targetUserId.value}",
    ) { parametersOf(workspaceId, targetUserId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()
    var snackbar by remember { mutableStateOf<String?>(null) }
    val unknownText = stringResource(Res.string.workspace_member_actions_error_unknown)
    val notOwnerText = stringResource(Res.string.workspace_member_actions_error_not_owner)
    val cannotDemoteText = stringResource(Res.string.workspace_member_actions_error_cannot_demote_owner)

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            MemberActionsEffect.Dismiss -> onDismiss()
            is MemberActionsEffect.ShowError -> {
                snackbar = when (effect.error) {
                    InviteError.NotOwner -> notOwnerText
                    InviteError.CannotDemoteLastOwner -> cannotDemoteText
                    else -> unknownText
                }
            }
        }
    }

    when (val s = state) {
        is MemberActionsState.Loading -> Spacer(Modifier.height(160.dp))
        is MemberActionsState.Content -> MemberActionsContent(state = s, onEvent = viewModel::onEvent)
    }

    snackbar?.let { msg ->
        AlertDialog(
            onDismissRequest = { snackbar = null },
            confirmButton = {
                TextButton(onClick = { snackbar = null }) {
                    Text(stringResource(Res.string.workspace_member_actions_confirm_remove_cancel))
                }
            },
            text = { Text(msg) },
        )
    }
}
