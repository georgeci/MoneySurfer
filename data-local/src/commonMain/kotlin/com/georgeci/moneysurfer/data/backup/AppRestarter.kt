package com.georgeci.moneysurfer.data.backup

/**
 * Restarts the host process so newly-imported on-disk state is observed.
 *
 * iOS cannot relaunch programmatically; the actual on iOS exits the process and
 * surfaces a "please reopen" message via the calling layer.
 */
expect class AppRestarter {
    fun restart()
}
