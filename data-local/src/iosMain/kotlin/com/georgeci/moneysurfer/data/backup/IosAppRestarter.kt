package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.domain.backup.AppRestarter
import platform.posix.exit

/**
 * iOS does not allow programmatic relaunch — App Review guidelines forbid it.
 * We exit cleanly; the calling layer must surface a "please reopen" message
 * before this fires.
 */
class IosAppRestarter : AppRestarter {
    override val requiresManualRelaunch: Boolean = true

    override fun restart() {
        exit(0)
    }
}
