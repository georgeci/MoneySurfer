package com.georgeci.moneysurfer.data.config

import android.content.Context
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import com.georgeci.moneysurfer.data.platform.isDebuggableBuild
import kotlinx.coroutines.CoroutineScope

/**
 * Debug layer for Android. Release APKs get [DebugConfigSource.Empty] — the layer stays in the
 * chain and always resolves absent.
 *
 * The signature takes a [Context] rather than being an `expect`/`actual` pair, because that is what
 * Android needs both for the debuggable check and for `filesDir`; iOS and the JVM need nothing.
 * `sharedPlatformModule` already resolves a `Context` on Android, so this is one call per platform
 * in a file that already exists per platform.
 */
fun createDebugConfigSource(context: Context, scope: CoroutineScope): DebugConfigSource =
    if (context.isDebuggableBuild()) {
        DebugConfigSourceImpl(
            createReplaceOnCorruptionDataStore(
                producePath = { context.filesDir.resolve(DEBUG_OVERRIDES_FILE_NAME).absolutePath },
            ),
            scope,
        )
    } else {
        DebugConfigSource.Empty
    }
