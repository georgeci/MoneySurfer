package com.georgeci.moneysurfer.feature.settings

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the offline-build gating for Settings. The profile/Name section,
 * the Sync section, Logout row, the Members row and the pending-invites row
 * are remote-only entry points; the offline build (no Firebase / sync) hides
 * them via the `isOffline` flag injected from `HostCapabilities`.
 */
class SettingsStateOfflineTest {

    @Test
    fun `online state with sync enabled shows profile, sync, logout, members and pending invites rows`() {
        val state = SettingsState(
            isOffline = false,
            syncEnabled = true,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        assertTrue(state.showProfile)
        assertTrue(state.showSyncSection)
        assertTrue(state.showLogout)
        assertTrue(state.showWorkspaceMembers)
        assertTrue(state.showPendingInvites)
        assertTrue(state.showDeleteAccount)
    }

    @Test
    fun `online state with sync disabled shows profile but hides only the sync section`() {
        val state = SettingsState(
            isOffline = false,
            syncEnabled = false,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        assertTrue(state.showProfile, "profile is gated by isOffline, not the sync flag")
        assertFalse(state.showSyncSection, "sync feature flag off hides the sync section")
        assertTrue(state.showLogout, "logout is gated by isOffline, not the sync flag")
        assertTrue(state.showWorkspaceMembers, "members are gated by isOffline, not the sync flag")
        assertTrue(state.showPendingInvites, "pending invites are gated by isOffline, not the sync flag")
    }

    @Test
    fun `offline state hides profile, sync, logout, members and pending invites rows`() {
        val state = SettingsState(
            isOffline = true,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        assertFalse(state.showProfile, "offline build has no auth identity to display")
        assertFalse(state.showSyncSection, "offline build has no sync backend")
        assertFalse(state.showLogout, "offline build has no auth session to log out of")
        assertFalse(state.showWorkspaceMembers, "members rely on remote membership data")
        assertFalse(state.showPendingInvites, "incoming invites flow needs the remote backend")
        assertFalse(state.showDeleteAccount, "offline build has no remote account to delete")
    }
}
