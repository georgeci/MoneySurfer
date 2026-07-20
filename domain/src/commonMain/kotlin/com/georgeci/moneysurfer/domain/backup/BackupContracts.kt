package com.georgeci.moneysurfer.domain.backup

import okio.BufferedSink
import okio.BufferedSource

interface BackupExporter {
    /**
     * Streams a complete backup ZIP into [sink].
     *
     * With a non-blank [passphrase] the data entries are sealed inside an
     * encrypted `payload.enc` entry ([BackupEncryptionInfo.SCHEME_V1]); the
     * container is still a plain ZIP with a readable manifest. With a null or
     * blank passphrase the archive is fully unencrypted.
     *
     * Caller owns [sink] lifecycle and must close it after this returns.
     * The function flushes but does not close. Suspends on a background
     * dispatcher; safe to invoke from the main thread.
     */
    suspend fun exportTo(sink: BufferedSink, passphrase: String? = null): Result<BackupManifest>
}

interface BackupImporter {
    /**
     * Peeks at [source] without consuming it and reports whether the backup
     * is passphrase-protected (so the UI can prompt before [importFrom]).
     * Returns `false` for unreadable input — [importFrom] surfaces the real
     * error in that case.
     */
    suspend fun isPassphraseProtected(source: BufferedSource): Boolean

    /**
     * Reads a backup ZIP from [source] and replaces the current local data.
     * Encrypted backups require the matching [passphrase]
     * ([BackupError.PassphraseRequired] / [BackupError.WrongPassphrase]).
     *
     * On success the caller must immediately trigger an app restart via
     * [AppRestarter] — the in-memory Koin singletons still reference closed
     * Room handles. Caller owns [source] and must close it.
     */
    suspend fun importFrom(source: BufferedSource, passphrase: String? = null): Result<BackupManifest>
}
