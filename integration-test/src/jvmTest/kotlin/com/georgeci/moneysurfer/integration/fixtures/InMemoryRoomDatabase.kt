package com.georgeci.moneysurfer.integration.fixtures

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * In-memory Room build of [MoneySurferDatabase] for integration tests. Each
 * test should construct its own (or reset between tests) — Room's in-memory
 * databases are scoped to the JVM process but distinct across calls.
 *
 * Mirrors the on-disk JVM builder (BundledSQLiteDriver + IO dispatcher) so
 * behaviour matches production except for persistence.
 */
fun inMemoryRoomDatabase(): MoneySurferDatabase =
    Room.inMemoryDatabaseBuilder<MoneySurferDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
