package com.georgeci.moneysurfer.data.remote

import platform.Foundation.NSProcessInfo

internal actual fun defaultEmulatorHost(): String = "localhost"

internal actual fun defaultUseEmulator(): Boolean {
    val raw = NSProcessInfo.processInfo.environment["MS_USE_EMULATOR"] as? String
    return raw?.equals("true", ignoreCase = true) == true
}
