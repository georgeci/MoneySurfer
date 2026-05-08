package com.georgeci.moneysurfer.data.backup

import platform.posix.exit

actual class AppRestarter {
    /**
     * iOS does not allow programmatic relaunch — the App Review guidelines
     * forbid it. We exit cleanly; the user must reopen the app manually.
     * The calling layer surfaces a "please reopen" message before this fires.
     */
    actual fun restart() {
        exit(0)
    }
}
