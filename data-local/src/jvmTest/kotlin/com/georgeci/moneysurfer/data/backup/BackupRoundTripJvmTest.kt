package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.fixtures.BackupTestHarness
import com.georgeci.moneysurfer.data.backup.fixtures.openDatabaseAt
import com.georgeci.moneysurfer.data.backup.fixtures.testAppInfo
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import okio.Buffer
import okio.FileSystem

/**
 * End-to-end round-trip across [BackupExporterImpl] and [BackupImporterImpl]
 * with a real on-disk Room database. Exercises:
 * - Seed entities survive the export/import cycle byte-for-byte.
 * - DataStore prefs file is restored.
 * - The sync DB file present pre-import is removed (so Room recreates it
 *   empty on the next start, clearing the outbox + sync_meta).
 */
class BackupRoundTripJvmTest : FunSpec({

    lateinit var harness: BackupTestHarness

    beforeEach { harness = BackupTestHarness() }
    afterEach { harness.close() }

    test("export then import restores seeded entities + datastore + clears sync DB") {
        val originalUser = UserEntity(id = "u-original", displayName = "Alice", isAnon = false)
        val datastorePayload = "PREFS:lang=ru,theme=dark"
        val locator = harness.locator

        // ---- 1. Seed source state ---------------------------------------
        harness.database().userDao().insert(originalUser)
        FileSystem.SYSTEM.write(locator.dataStoreFile()) { writeUtf8(datastorePayload) }
        // Pre-existing sync DB the importer should sweep away.
        FileSystem.SYSTEM.write(locator.syncDbFile()) { writeUtf8("stale-sync-state") }

        // ---- 2. Export --------------------------------------------------
        val backup = Buffer()
        BackupExporterImpl(harness.database(), locator, testAppInfo).exportTo(backup).getOrThrow()
        val backupCopy = Buffer().also { backup.copyTo(it) }

        // ---- 3. Replace local state with something completely different ----
        harness.database().close()
        FileSystem.SYSTEM.deleteIfExists(locator.moneySurferDbFile())
        FileSystem.SYSTEM.deleteIfExists(locator.dataStoreFile())
        FileSystem.SYSTEM.write(locator.syncDbFile()) { writeUtf8("post-wipe-stale") }

        run {
            val db = openDatabaseAt(locator.moneySurferDbFile())
            try {
                db.userDao().insert(UserEntity(id = "u-other", displayName = "Bob", isAnon = false))
            } finally {
                db.close()
            }
        }
        FileSystem.SYSTEM.write(locator.dataStoreFile()) { writeUtf8("PREFS:overwritten") }

        // ---- 4. Import --------------------------------------------------
        val importTargetDb = openDatabaseAt(locator.moneySurferDbFile())
        BackupImporterImpl(importTargetDb, locator).importFrom(backupCopy).getOrThrow()

        // ---- 5. Assertions -----------------------------------------------
        // Sync DB must have been wiped — Room rebuilds it empty on next launch.
        FileSystem.SYSTEM.exists(locator.syncDbFile()) shouldBe false

        // DataStore restored to its pre-export contents.
        val restoredPrefs = FileSystem.SYSTEM.read(locator.dataStoreFile()) { readUtf8() }
        restoredPrefs shouldBe datastorePayload

        // Reopen the DB at the restored path and verify the seed user is back
        // (and the "u-other" inserted between export and import is gone).
        val verifyDb = openDatabaseAt(locator.moneySurferDbFile())
        try {
            val users = verifyDb.userDao().getAll().first()
            users.map { it.id } shouldContainExactly listOf("u-original")
            verifyDb.userDao().getById("u-original") shouldBe originalUser
        } finally {
            verifyDb.close()
        }
    }
})
