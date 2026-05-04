package com.georgeci.moneysurfer.feature.workspace.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.utils.HandleSideEffect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the Members route. The surrounding [androidx.compose.material3.ModalBottomSheet]
 * is provided by [com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy] — this
 * composable renders only the sheet body. Side-effects bubble up to the host so it can navigate
 * to invite / member-actions destinations.
 */
@Composable
fun WorkspaceMembersBottomSheet(
    workspaceId: WorkspaceId,
    onDismiss: () -> Unit,
    onNavigateToInvite: (WorkspaceId) -> Unit,
    onNavigateToMemberActions: (WorkspaceId, UserId) -> Unit,
    viewModel: WorkspaceMembersViewModel = koinViewModel(
        key = workspaceId.value,
    ) { parametersOf(workspaceId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceMembersEffect.Dismiss -> onDismiss()
            WorkspaceMembersEffect.OpenInvite -> onNavigateToInvite(workspaceId)
            is WorkspaceMembersEffect.OpenMemberActions ->
                onNavigateToMemberActions(effect.workspaceId, effect.targetUserId)
            WorkspaceMembersEffect.LeftWorkspace -> onDismiss()
            is WorkspaceMembersEffect.ShowError -> Unit // could surface a snackbar via host
        }
    }

    WorkspaceMembersScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}
