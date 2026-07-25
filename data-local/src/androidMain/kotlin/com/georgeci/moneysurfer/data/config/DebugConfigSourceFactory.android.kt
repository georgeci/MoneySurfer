package com.georgeci.moneysurfer.data.config

import android.content.Context
import android.content.pm.ApplicationInfo
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createDataStore

/**
 * Debug layer for Android. Release APKs get [DebugConfigSource.Empty] — the layer stays in the
 * chain and always resolves absent.
 *
 * The signature takes a [Context] rather than being an `expect`/`actual` pair, because that is what
 * Android needs both for the debuggable check and for `filesDir`; iOS and the JVM need nothing.
 * `sharedPlatformModule` already resolves a `Context` on Android, so this is one call per platform
 * in a file that already exists per platform.
 */
fun createDebugConfigSource(context: Context): DebugConfigSource =
    if (context.isDebuggable()) {
        DebugConfigSourceImpl(
            createDataStore(
                producePath = { context.filesDir.resolve(DEBUG_OVERRIDES_FILE_NAME).absolutePath },
            ),
        )
    } else {
        DebugConfigSource.Empty
    }

/**
 * `BuildConfig.DEBUG` belongs to whichever Gradle module it is generated in, and this is a library
 * module — the installed application's own `FLAG_DEBUGGABLE` is the signal that actually tracks the
 * APK the user is running.
 */
private fun Context.isDebuggable(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
