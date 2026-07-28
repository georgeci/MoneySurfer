package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One synced user setting — the storage behind every `SettingKey` declared `sync = true`.
 *
 * Room rather than DataStore for three reasons, in order of weight: the push plugin needs
 * `value + updatedAt` readable **by key** at push time, LWW needs a real `updatedAt` column to
 * compare against the remote document, and the storage boundary then coincides with the reset
 * boundary — this table joins the DAO fan-out in `LocalDataResetRepositoryImpl.clearAll()`, so an
 * account change cannot leave the previous user's settings behind. `sync = false` keys stay in
 * DataStore precisely because they must *not* be wiped (`ui.onboarding_completed`) or replicated.
 *
 * It is **not** transactional atomicity with the outbox: that lives in `SyncDatabase`
 * (`moneysurfer_sync.db`), a different database, so no table here can share a transaction with it.
 * The write-then-crash window is closed by the sign-in reconciliation instead — see
 * [lastPushedAt].
 *
 * No foreign key and no workspace column: settings are user-scoped, not workspace-scoped, which is
 * also why the matching sync plugin declares `scopeKey = null`.
 */
@Entity(tableName = "config_entry")
data class ConfigEntryEntity(
    /** The key *name* (`ui.theme_mode`), unprefixed — the same string used as the Firestore doc id. */
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    /** Codec-encoded value. Always a string: that is what every backing store has in common. */
    @ColumnInfo(name = "value") val value: String,
    /** Client-clock epoch millis of the last local write, and the basis of per-key LWW. */
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    /**
     * `updatedAt` of the value last known to have reached Firestore, or `null` for a row that has
     * never been pushed.
     *
     * This is what makes the sign-in reconciliation cheap and exact: `OutboxEnqueuerImpl` silently
     * drops writes made in demo or signed-out sessions and nothing re-enqueues them, so before the
     * first pull of a real session every row with `lastPushedAt IS NULL OR updatedAt > lastPushedAt`
     * is queued. A pulled value is stamped as already-pushed, so a pull never provokes an echo push.
     */
    @ColumnInfo(name = "lastPushedAt") val lastPushedAt: Long? = null,
)
