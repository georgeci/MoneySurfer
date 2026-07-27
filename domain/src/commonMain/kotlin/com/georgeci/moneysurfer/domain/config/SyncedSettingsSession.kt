package com.georgeci.moneysurfer.domain.config

/**
 * Session-boundary maintenance for the settings that replicate per user.
 *
 * A domain facade for the same reason `ConfigHydration` and `RemoteConfigRefresh` are: the callers
 * are session-lifecycle use cases in `domain`, which must not see `app-config` any more than a
 * feature may.
 */
interface SyncedSettingsSession {

    /**
     * Run at the start of every session, before its first pull. Two jobs, both of which have to
     * happen there and nowhere else:
     *
     * - **Drop the logout overlay.** The previous user's theme is held in memory so the UI does not
     *   snap to defaults mid-session; left in place it would shadow whatever this user's pull writes
     *   into local storage, which is the leak the account wipe exists to prevent.
     * - **Re-enqueue writes the outbox refused.** `OutboxEnqueuerImpl` silently no-ops for demo and
     *   signed-out sessions and nothing else replays those writes, so every setting whose stored
     *   value never reached the server is queued now. This also closes the window between a local
     *   write and its enqueue, which no shared transaction can protect — the two stores are
     *   different databases.
     *
     * Idempotent, and safe without a Firebase session: the enqueue itself no-ops there.
     */
    suspend fun onSessionStart()
}
