package com.georgeci.moneysurfer.feature.workspace

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.feature.workspace.creation.WorkspaceCreationScreen
import com.georgeci.moneysurfer.feature.workspace.incoming.IncomingInvitesScreen
import com.georgeci.moneysurfer.feature.workspace.invite.WorkspaceInviteScreen
import com.georgeci.moneysurfer.feature.workspace.members.MemberActionsBottomSheet
import com.georgeci.moneysurfer.feature.workspace.members.WorkspaceManageScreen
import com.georgeci.moneysurfer.feature.workspace.members.WorkspaceMembersBottomSheet
import com.georgeci.moneysurfer.feature.workspace.selector.WorkspaceSelectorNavigation
import com.georgeci.moneysurfer.feature.workspace.selector.WorkspaceSelectorScreen
import com.georgeci.moneysurfer.navigation.BottomSheetSceneStrategy
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
val workspaceNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.WorkspaceSelector> { key ->
        WorkspaceSelectorScreen(
            showActions = key.showActions,
            navigation = WorkspaceSelectorNavigation(
                onNavigateToDashboard = {
                    navigator.replaceTop(Route.Dashboard)
                },
                // `resetTo`: logout wiped the local data, so nothing behind this entry is still valid.
                onNavigateToSignIn = { navigator.resetTo(Route.SignIn) },
                onNavigateToWorkspaceCreation = { navigator.push(Route.WorkspaceCreation()) },
                onNavigateToWorkspaceEdit = { workspaceId ->
                    navigator.push(Route.WorkspaceCreation(workspaceId = workspaceId.value))
                },
                onNavigateToWorkspaceMembers = { workspaceId ->
                    navigator.push(Route.WorkspaceMembers(workspaceId = workspaceId.value))
                },
            ),
            cloudDataUnavailable = key.cloudDataUnavailable,
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

    entry<Route.WorkspaceManage>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) { key ->
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

    entry<Route.WorkspaceInvite>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) { key ->
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
