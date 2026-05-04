package com.georgeci.moneysurfer.data.remote

internal actual fun defaultEmulatorHost(): String = "localhost"

internal actual fun defaultUseEmulator(): Boolean =
    System.getenv("MS_USE_EMULATOR")?.equals("true", ignoreCase = true) == true
