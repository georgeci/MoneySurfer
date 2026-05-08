package com.georgeci.moneysurfer.feature.settings

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the offline-build gating for Settings. The Sync section, Logout
 * row and the Members row in the workspace section are remote-only entry
 * points; the offline build (no Firebase / sync) hides them via the
 * `isOffline` flag injected from `OfflineBuildFlags`.
 */
class SettingsStateOfflineTest {

    @Test
    fun `online state shows sync, logout and members rows`() {
        val state = SettingsState(
            isOffline = false,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        assertTrue(state.showSyncSection)
        assertTrue(state.showLogout)
        assertTrue(state.showWorkspaceMembers)
    }

    @Test
    fun `offline state hides sync, logout and members rows`() {
        val state = SettingsState(
            isOffline = true,
            currentWorkspaceId = WorkspaceId("ws-1"),
        )

        assertFalse(state.showSyncSection, "offline build has no sync backend")
        assertFalse(state.showLogout, "offline build has no auth session to log out of")
        assertFalse(state.showWorkspaceMembers, "members rely on remote membership data")
    }
}
