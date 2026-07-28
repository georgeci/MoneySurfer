package com.georgeci.moneysurfer.feature.settings

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Locks in the offline-build gating for Settings. The profile/Name section,
 * the Sync section, Logout row, the Members row and the pending-invites row
 * are remote-only entry points; the offline build (no Firebase / sync) hides
 * them via the `isOffline` flag injected from `HostCapabilities`.
 */
class SettingsStateOfflineTest : StringSpec({

    "online state with sync enabled shows profile, sync, logout, members and pending invites rows" {
        val state = SettingsState(
            isOffline = false,
            syncEnabled = true,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        state.showProfile shouldBe true
        state.showSyncSection shouldBe true
        state.showLogout shouldBe true
        state.showWorkspaceMembers shouldBe true
        state.showPendingInvites shouldBe true
        state.showDeleteAccount shouldBe true
    }

    "online state with sync disabled shows profile but hides only the sync section" {
        val state = SettingsState(
            isOffline = false,
            syncEnabled = false,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        withClue("profile is gated by isOffline, not the sync flag") {
            state.showProfile shouldBe true
        }
        withClue("sync feature flag off hides the sync section") {
            state.showSyncSection shouldBe false
        }
        withClue("logout is gated by isOffline, not the sync flag") {
            state.showLogout shouldBe true
        }
        withClue("members are gated by isOffline, not the sync flag") {
            state.showWorkspaceMembers shouldBe true
        }
        withClue("pending invites are gated by isOffline, not the sync flag") {
            state.showPendingInvites shouldBe true
        }
    }

    "offline state hides profile, sync, logout, members and pending invites rows" {
        val state = SettingsState(
            isOffline = true,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        withClue("offline build has no auth identity to display") {
            state.showProfile shouldBe false
        }
        withClue("offline build has no sync backend") {
            state.showSyncSection shouldBe false
        }
        withClue("offline build has no auth session to log out of") {
            state.showLogout shouldBe false
        }
        withClue("members rely on remote membership data") {
            state.showWorkspaceMembers shouldBe false
        }
        withClue("incoming invites flow needs the remote backend") {
            state.showPendingInvites shouldBe false
        }
        withClue("offline build has no remote account to delete") {
            state.showDeleteAccount shouldBe false
        }
    }
})
