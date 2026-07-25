package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.domain.storage.iosAppStorageFilePath
import kotlin.experimental.ExperimentalNativeApi

/**
 * Debug layer for iOS. `Platform.isDebugBinary` is the same signal the host already passes to
 * `initKoin(isDebug = ...)`, so the panel appears exactly where debug logging does.
 */
@OptIn(ExperimentalNativeApi::class)
fun createDebugConfigSource(): DebugConfigSource =
    if (kotlin.native.Platform.isDebugBinary) {
        DebugConfigSourceImpl(
            createDataStore(
                producePath = { iosAppStorageFilePath(DEBUG_OVERRIDES_FILE_NAME, isDatabase = false) },
            ),
        )
    } else {
        DebugConfigSource.Empty
    }
