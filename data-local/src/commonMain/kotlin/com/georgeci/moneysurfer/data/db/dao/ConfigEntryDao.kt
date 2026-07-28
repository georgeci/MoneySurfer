package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access to the synced-settings table.
 *
 * Reads are whole-table: the synced set is a handful of rows, both consumers (the in-memory mirror
 * behind `ConfigSource.peek`, and the reconciliation) want all of them, and a per-key query would
 * buy nothing. The writes, by contrast, are deliberately three narrow statements rather than one
 * `@Upsert`, because each has to leave a *different* column alone — see each one.
 */
@Dao
interface ConfigEntryDao {

    /**
     * Every row, re-emitted on any write to the table — including one made by the pull, which is
     * what lets a setting changed on another device retheme this one's UI without any extra wiring.
     */
    @Query("SELECT * FROM config_entry")
    fun observeAll(): Flow<List<ConfigEntryEntity>>

    @Query("SELECT * FROM config_entry")
    suspend fun getAll(): List<ConfigEntryEntity>

    @Query("SELECT * FROM config_entry WHERE `key` = :key")
    suspend fun getByKey(key: String): ConfigEntryEntity?

    /**
     * A local write. `lastPushedAt` is *not* in the update list, so an existing row keeps whatever
     * it was: the row now reads newer than its last push, which is exactly what makes the
     * reconciliation pick it up if the outbox refused the enqueue.
     */
    @Query(
        """
        INSERT INTO config_entry (`key`, `value`, `updatedAt`, `lastPushedAt`)
        VALUES (:key, :value, :updatedAt, NULL)
        ON CONFLICT(`key`) DO UPDATE SET
            `value` = :value,
            `updatedAt` = :updatedAt
        """,
    )
    suspend fun write(key: String, value: String, updatedAt: Long)

    /**
     * A pulled value. Stamps `lastPushedAt = updatedAt` because the server is where this value came
     * from — leaving it null would make the next reconciliation push the value straight back.
     */
    @Query(
        """
        INSERT INTO config_entry (`key`, `value`, `updatedAt`, `lastPushedAt`)
        VALUES (:key, :value, :updatedAt, :updatedAt)
        ON CONFLICT(`key`) DO UPDATE SET
            `value` = :value,
            `updatedAt` = :updatedAt,
            `lastPushedAt` = :updatedAt
        """,
    )
    suspend fun applyRemote(key: String, value: String, updatedAt: Long)

    /**
     * Records a successful push. Scoped to `updatedAt = :pushedUpdatedAt` so a local write that
     * landed *after* the push read the row is not marked as pushed — that write must still be
     * reconciled, and the value the server holds is the older one.
     */
    @Query(
        """
        UPDATE config_entry
        SET `lastPushedAt` = :pushedUpdatedAt
        WHERE `key` = :key AND `updatedAt` = :pushedUpdatedAt
        """,
    )
    suspend fun markPushed(key: String, pushedUpdatedAt: Long)

    /** Keys whose current value has never reached the server. */
    @Query("SELECT `key` FROM config_entry WHERE `lastPushedAt` IS NULL OR `updatedAt` > `lastPushedAt`")
    suspend fun keysPendingPush(): List<String>

    @Query("DELETE FROM config_entry")
    suspend fun deleteAll()
}
