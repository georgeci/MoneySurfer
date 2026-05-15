package com.georgeci.moneysurfer.feature.workspace.creation

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Locks in the offline-build gating for the workspace creation/edit screen. The Members
 * section (invite row + member list) is remote-only; the offline build hides it via the
 * `isOffline` flag injected from `OfflineBuildFlags`.
 */
class WorkspaceCreationStateOfflineTest : StringSpec({

    fun content(isEditing: Boolean, isOffline: Boolean) = WorkspaceCreationState.Content(
        workspaceId = WorkspaceId("ws-1").takeIf { isEditing },
        name = "Personal",
        description = "",
        currency = "EUR",
        currencies = emptyList(),
        members = emptyList(),
        isEditing = isEditing,
        isOffline = isOffline,
    )

    "online edit state shows the Members section" {
        content(isEditing = true, isOffline = false).showMembersSection shouldBe true
    }

    "offline edit state hides the Members section" {
        content(isEditing = true, isOffline = true).showMembersSection shouldBe false
    }

    "creation state never shows the Members section" {
        content(isEditing = false, isOffline = false).showMembersSection shouldBe false
    }
})
