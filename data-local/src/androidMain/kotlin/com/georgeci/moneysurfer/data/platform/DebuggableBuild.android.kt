package com.georgeci.moneysurfer.data.platform

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * `BuildConfig.DEBUG` belongs to whichever Gradle module it is generated in, and the persistence
 * layer lives in library modules — the installed application's own `FLAG_DEBUGGABLE` is the signal
 * that actually tracks the APK the user is running.
 *
 * This is the Android counterpart of `kotlin.native.Platform.isDebugBinary` on iOS, and it gates
 * both the debug-overrides config layer and the destructive-migration fallback.
 */
fun Context.isDebuggableBuild(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
