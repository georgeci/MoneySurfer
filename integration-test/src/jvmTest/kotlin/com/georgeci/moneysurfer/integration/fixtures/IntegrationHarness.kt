package com.georgeci.moneysurfer.integration.fixtures

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.sync.db.SyncDatabase

/**
 * One-stop wiring of real `:data` impls for an integration test.
 *
 * Phase 3 covers **Room-level integration only**. gitlive's "JVM" Firebase
 * artifact is actually the Android sources repackaged — `firebase-app-jvm-2.4.0.jar`
 * imports `android.content.Context` and casts the supplied context to it,
 * crashing on a plain JVM with `ClassCastException`. Real SDK-on-emulator
 * coverage moves to:
 * - Phase 4 (Maestro on Android device) for end-to-end user flows,
 * - or a future `androidDeviceTest` target running the same `IntegrationHarness`
 *   shape under a real Android Firebase SDK.
 *
 * Lifecycle: build with `IntegrationHarness()`, drive your test, call [close]
 * (or wrap in `try/finally`) so the in-memory Room handle releases its
 * native SQLite resources.
 */
class IntegrationHarness {
    val database: MoneySurferDatabase = inMemoryRoomDatabase()
    val syncDatabase: SyncDatabase = inMemorySyncDatabase()

    fun close() {
        database.close()
        syncDatabase.close()
    }
}
