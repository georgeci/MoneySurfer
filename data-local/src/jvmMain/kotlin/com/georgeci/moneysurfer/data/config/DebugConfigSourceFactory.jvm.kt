package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import com.georgeci.moneysurfer.domain.storage.appDataDir
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Debug layer for the desktop JVM build, which is developer-only today — `main.kt` already starts
 * Koin with `isDebug = true`. Revisit together with that flag if a packaged desktop release ships.
 */
fun createDebugConfigSource(scope: CoroutineScope): DebugConfigSource = DebugConfigSourceImpl(
    createReplaceOnCorruptionDataStore(
        producePath = { File(appDataDir(), DEBUG_OVERRIDES_FILE_NAME).absolutePath },
    ),
    scope,
)
