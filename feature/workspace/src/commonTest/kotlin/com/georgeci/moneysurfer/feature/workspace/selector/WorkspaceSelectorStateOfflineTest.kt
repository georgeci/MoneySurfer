package com.georgeci.moneysurfer.feature.workspace.selector

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Locks in the offline-build gating for the workspace selector. The per-row "Members"
 * action is remote-only — offline workspaces are always single-user, so the offline
 * build hides it via the `isOffline` flag injected from `OfflineBuildFlags`.
 */
class WorkspaceSelectorStateOfflineTest : StringSpec({

    fun content(isOffline: Boolean) = WorkspaceSelectorState.Content(
        workspaces = emptyList(),
        selectedWorkspaceId = null,
        pendingWorkspaceId = null,
        showActions = true,
        isSelecting = false,
        isOffline = isOffline,
    )

    "online state shows the Members row action" {
        content(isOffline = false).showMemberActions shouldBe true
    }

    "offline state hides the Members row action" {
        content(isOffline = true).showMemberActions shouldBe false
    }
})
