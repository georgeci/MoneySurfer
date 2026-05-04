package com.georgeci.moneysurfer.data.remote

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

internal actual fun defaultEmulatorHost(): String = "localhost"

internal actual fun defaultUseEmulator(): Boolean {
    val raw = NSProcessInfo.processInfo.environment["MS_USE_EMULATOR"] as? String
    val plist = NSBundle.mainBundle.objectForInfoDictionaryKey("MS_USE_EMULATOR")?.toString()
    return raw.isTrue() || plist.isTrue()
}

private fun String?.isTrue(): Boolean = this?.equals("true", ignoreCase = true) == true || this == "1" || this == "YES"
