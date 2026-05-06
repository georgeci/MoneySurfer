package com.georgeci.moneysurfer.feature.workspace.members

import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId

internal val PreviewRoster = listOf(
    MemberUi(UserId("u-1"), "Kasia M.", "kasia@mail.com", WorkspaceRole.OWNER, "K", isYou = false),
    MemberUi(UserId("u-2"), "Julian P.", "julian@mail.com", WorkspaceRole.EDITOR, "J", isYou = true),
    MemberUi(UserId("u-3"), "Asia W.", "asia@mail.com", WorkspaceRole.EDITOR, "A", isYou = false),
    MemberUi(UserId("u-4"), "Tom N.", "tom@mail.com", WorkspaceRole.VIEWER, "T", isYou = false),
)

internal val PreviewInvites = listOf(
    Triple("i-1", "kasia.nowak@gmail.com", WorkspaceRole.EDITOR) to false,
    Triple("i-2", "pavel@mail.com", WorkspaceRole.VIEWER) to false,
    Triple("i-3", "lena@mail.com", WorkspaceRole.VIEWER) to true,
).map { (data, expired) ->
    val (id, email, role) = data
    InviteUi(
        id = WorkspaceInviteId(id),
        email = email,
        role = role,
        isExpired = expired,
        expiresAt = kotlin.time.Instant.fromEpochMilliseconds(0),
    )
}

internal fun previewState(
    viewerRole: WorkspaceRole?,
    tab: MembersTab = MembersTab.Active,
    members: List<MemberUi> = PreviewRoster,
    invites: List<InviteUi> = PreviewInvites,
    showLeaveDialog: Boolean = false,
    busy: Boolean = false,
): WorkspaceMembersState.Content = WorkspaceMembersState.Content(
    workspaceId = WorkspaceId("preview-ws-1"),
    workspaceName = "Family",
    currentUserId = UserId("u-2"),
    viewerRole = viewerRole,
    members = members,
    pendingInvites = invites,
    tab = tab,
    busy = busy,
    showLeaveDialog = showLeaveDialog,
)
