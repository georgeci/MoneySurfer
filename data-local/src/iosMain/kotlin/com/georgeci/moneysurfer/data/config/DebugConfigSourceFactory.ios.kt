package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import com.georgeci.moneysurfer.domain.storage.iosAppStorageFilePath
import kotlinx.coroutines.CoroutineScope
import kotlin.experimental.ExperimentalNativeApi

/**
 * Debug layer for iOS. `Platform.isDebugBinary` is the same signal the host already passes to
 * `initKoin(isDebug = ...)`, so the panel appears exactly where debug logging does.
 */
@OptIn(ExperimentalNativeApi::class)
fun createDebugConfigSource(scope: CoroutineScope): DebugConfigSource =
    if (kotlin.native.Platform.isDebugBinary) {
        DebugConfigSourceImpl(
            createReplaceOnCorruptionDataStore(
                producePath = { iosAppStorageFilePath(DEBUG_OVERRIDES_FILE_NAME, isDatabase = false) },
            ),
            scope,
        )
    } else {
        DebugConfigSource.Empty
    }
