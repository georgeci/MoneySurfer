package com.georgeci.moneysurfer.domain.backup

import okio.BufferedSink
import okio.BufferedSource

interface BackupExporter {
    /**
     * Streams a complete backup ZIP into [sink].
     *
     * Caller owns [sink] lifecycle and must close it after this returns.
     * The function flushes but does not close. Suspends on a background
     * dispatcher; safe to invoke from the main thread.
     */
    suspend fun exportTo(sink: BufferedSink): Result<BackupManifest>
}

interface BackupImporter {
    /**
     * Reads a backup ZIP from [source] and replaces the current local data.
     *
     * On success the caller must immediately trigger an app restart via
     * [AppRestarter] — the in-memory Koin singletons still reference closed
     * Room handles. Caller owns [source] and must close it.
     */
    suspend fun importFrom(source: BufferedSource): Result<BackupManifest>
}
