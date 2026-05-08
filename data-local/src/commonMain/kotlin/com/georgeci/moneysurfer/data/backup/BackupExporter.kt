package com.georgeci.moneysurfer.data.backup

import okio.BufferedSink

interface BackupExporter {
    /**
     * Streams a complete backup ZIP into [sink].
     *
     * Caller owns [sink] lifecycle and must close it after this suspends out
     * (success or failure). The function flushes but does not close.
     */
    suspend fun exportTo(sink: BufferedSink): Result<BackupManifest>
}
