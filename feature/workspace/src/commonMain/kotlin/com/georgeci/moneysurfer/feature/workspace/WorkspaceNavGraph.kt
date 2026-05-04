package com.georgeci.moneysurfer.feature.workspace

import androidx.compose.material3.ExperimentalMaterial3Api
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.workspace.creation.WorkspaceCreationScreen
import com.georgeci.moneysurfer.feature.workspace.incoming.IncomingInvitesScreen
import com.georgeci.moneysurfer.feature.workspace.invite.WorkspaceInviteScreen
import com.georgeci.moneysurfer.feature.workspace.members.MemberActionsBottomSheet
import com.georgeci.moneysurfer.feature.workspace.members.WorkspaceManageScreen
import com.georgeci.moneysurfer.feature.workspace.members.WorkspaceMembersBottomSheet
import com.georgeci.moneysurfer.feature.workspace.selector.WorkspaceSelectorScreen
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
val workspaceNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.WorkspaceSelector> { key ->
        WorkspaceSelectorScreen(
            showActions = key.showActions,
            onNavigateToDashboard = {
                navigator.replaceTop(Route.Dashboard)
            },
            onNavigateToWorkspaceCreation = { navigator.push(Route.WorkspaceCreation()) },
            onNavigateToWorkspaceEdit = { workspaceId ->
                navigator.push(Route.WorkspaceCreation(workspaceId = workspaceId.value))
            },
            onNavigateToWorkspaceMembers = { workspaceId ->
                navigator.push(Route.WorkspaceMembers(workspaceId = workspaceId.value))
            },
        )
    }

    entry<Route.WorkspaceCreation> { key ->
        WorkspaceCreationScreen(
            workspaceId = key.workspaceId?.let { WorkspaceId(it) },
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.WorkspaceMembers>(
        metadata = BottomSheetSceneStrategy.bottomSheet(),
    ) { key ->
        WorkspaceMembersBottomSheet(
            workspaceId = WorkspaceId(key.workspaceId),
            onDismiss = { navigator.pop() },
            onNavigateToInvite = { workspaceId ->
                navigator.push(Route.WorkspaceInvite(workspaceId = workspaceId.value))
            },
            onNavigateToMemberActions = { workspaceId, userId ->
                navigator.push(
                    Route.WorkspaceMemberActions(
                        workspaceId = workspaceId.value,
                        targetUserId = userId.value,
                    ),
                )
            },
        )
    }

    entry<Route.WorkspaceManage> { key ->
        WorkspaceManageScreen(
            workspaceId = WorkspaceId(key.workspaceId),
            onNavigateBack = { navigator.pop() },
            onNavigateToInvite = { workspaceId ->
                navigator.push(Route.WorkspaceInvite(workspaceId = workspaceId.value))
            },
            onNavigateToMemberActions = { workspaceId, userId ->
                navigator.push(
                    Route.WorkspaceMemberActions(
                        workspaceId = workspaceId.value,
                        targetUserId = userId.value,
                    ),
                )
            },
        )
    }

    entry<Route.WorkspaceInvite> { key ->
        WorkspaceInviteScreen(
            workspaceId = WorkspaceId(key.workspaceId),
            onNavigateBack = { navigator.pop() },
        )
    }

    entry<Route.WorkspaceMemberActions>(
        metadata = BottomSheetSceneStrategy.bottomSheet(),
    ) { key ->
        MemberActionsBottomSheet(
            workspaceId = WorkspaceId(key.workspaceId),
            targetUserId = UserId(key.targetUserId),
            onDismiss = { navigator.pop() },
        )
    }

    entry<Route.IncomingInvites> {
        IncomingInvitesScreen(onNavigateBack = { navigator.pop() })
    }
}
