package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.crypto.EncryptedBackupCodec
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredReader
import com.georgeci.moneysurfer.data.db.MONEY_SURFER_DB_VERSION
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.backup.BackupEncryptionInfo
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupImporter
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.blackholeSink
import okio.buffer
import okio.use
import org.koin.core.annotation.Single

@Single(binds = [BackupImporter::class])
class BackupImporterImpl(
    private val database: MoneySurferDatabase,
    private val locator: BackupStorageLocator,
) : BackupImporter {

    private val mutex = Mutex()
    private val fs: FileSystem = FileSystem.SYSTEM

    override suspend fun isPassphraseProtected(source: BufferedSource): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                readManifestEntry(ZipStoredReader(source.peek())).encryption != null
            }.getOrDefault(false)
        }

    override suspend fun importFrom(
        source: BufferedSource,
        passphrase: String?,
    ): Result<BackupManifest> = mutex.withLock {
        try {
            Result.success(withContext(Dispatchers.IO) { runImport(source, passphrase) })
        } catch (ce: CancellationException) {
            throw ce
        } catch (be: BackupError) {
            Result.failure(be)
        } catch (
            @Suppress("TooGenericExceptionCaught") t: Throwable,
        ) {
            Result.failure(BackupError.Io(t))
        }
    }

    private fun runImport(source: BufferedSource, passphrase: String?): BackupManifest {
        val reader = ZipStoredReader(source)
        val manifest = readManifestEntry(reader)
        return if (manifest.encryption != null) {
            importEncrypted(reader, manifest, passphrase)
        } else {
            importPlain(reader, manifest)
        }
    }

    /**
     * Unwraps an encrypted backup: the outer manifest only advertises the
     * scheme; the sealed `payload.enc` entry holds a complete plain archive
     * whose own manifest is the authenticated one (the codec's MAC covers it),
     * so the plain path re-validates everything after decryption.
     */
    private fun importEncrypted(
        reader: ZipStoredReader,
        outerManifest: BackupManifest,
        passphrase: String?,
    ): BackupManifest {
        val scheme = outerManifest.encryption?.scheme
        val error = when {
            scheme != BackupEncryptionInfo.SCHEME_V1 ->
                BackupError.InvalidArchive("Unsupported encryption scheme: $scheme")
            passphrase.isNullOrEmpty() -> BackupError.PassphraseRequired
            else -> null
        }
        if (error != null) {
            throw error
        }
        // Fail fast on the (unauthenticated) outer version fields before the
        // costly key derivation; the inner manifest is re-checked afterwards.
        validateVersions(outerManifest)

        val sealed = readEntry(reader, BackupManifest.ENCRYPTED_PAYLOAD_ENTRY_NAME)
        val innerZip = EncryptedBackupCodec.open(sealed.readByteArray(), checkNotNull(passphrase))

        val innerReader = ZipStoredReader(Buffer().apply { write(innerZip) })
        val innerManifest = readManifestEntry(innerReader)
        if (innerManifest.encryption != null) {
            throw BackupError.InvalidArchive("Nested encrypted payloads are not supported")
        }
        return importPlain(innerReader, innerManifest)
    }

    private fun importPlain(reader: ZipStoredReader, manifest: BackupManifest): BackupManifest {
        validateManifest(manifest)

        val mainDbPath = locator.moneySurferDbFile()
        val dataStorePath = locator.dataStoreFile()
        val syncDbPath = locator.syncDbFile()

        val mainDbTemp = mainDbPath.appendSuffix(SUFFIX_RESTORE_TMP)
        val dataStoreTemp = dataStorePath.appendSuffix(SUFFIX_RESTORE_TMP)

        runCatching {
            extractRemainingEntries(reader, mainDbTemp, dataStoreTemp)
            RestoredDatabaseVerifier.verify(fs, mainDbTemp)
        }
            .onFailure { cleanUpTempFiles(mainDbTemp, dataStoreTemp) }
            .getOrThrow()

        database.close()

        val mainDbBackup = mainDbPath.appendSuffix(SUFFIX_PRE_IMPORT)
        val dataStoreBackup = dataStorePath.appendSuffix(SUFFIX_PRE_IMPORT)

        try {
            fs.deleteIfExists(mainDbBackup)
            fs.deleteIfExists(dataStoreBackup)
            if (fs.exists(mainDbPath)) fs.atomicMove(mainDbPath, mainDbBackup)
            if (fs.exists(dataStorePath)) fs.atomicMove(dataStorePath, dataStoreBackup)
            mainDbPath.parent?.let { fs.createDirectories(it) }
            dataStorePath.parent?.let { fs.createDirectories(it) }
            fs.atomicMove(mainDbTemp, mainDbPath)
            fs.atomicMove(dataStoreTemp, dataStorePath)

            // Wipe stale WAL/SHM next to the main DB so SQLite re-creates fresh ones.
            fs.deleteIfExists(mainDbPath.appendSuffix(SUFFIX_WAL))
            fs.deleteIfExists(mainDbPath.appendSuffix(SUFFIX_SHM))

            // Drop sync state entirely; Room rebuilds an empty file on next start, so the
            // outbox + sync_meta are clean and the next sync pulls fresh from cloud.
            fs.deleteIfExists(syncDbPath)
            fs.deleteIfExists(syncDbPath.appendSuffix(SUFFIX_WAL))
            fs.deleteIfExists(syncDbPath.appendSuffix(SUFFIX_SHM))

            fs.deleteIfExists(mainDbBackup)
            fs.deleteIfExists(dataStoreBackup)
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            rollback(mainDbPath, mainDbBackup)
            rollback(dataStorePath, dataStoreBackup)
            throw error
        }

        return manifest
    }

    private fun cleanUpTempFiles(mainDbTemp: Path, dataStoreTemp: Path) {
        fs.deleteIfExists(mainDbTemp)
        fs.deleteIfExists(dataStoreTemp)
        // Side files a WAL-mode open of the temp DB may have left behind.
        fs.deleteIfExists(mainDbTemp.appendSuffix(SUFFIX_WAL))
        fs.deleteIfExists(mainDbTemp.appendSuffix(SUFFIX_SHM))
    }

    private fun readManifestEntry(reader: ZipStoredReader): BackupManifest {
        val firstName = reader.nextEntryName()
        val problem = when {
            firstName == null -> "Empty archive"
            firstName != BackupManifest.MANIFEST_ENTRY_NAME ->
                "Expected ${BackupManifest.MANIFEST_ENTRY_NAME} as first entry; got $firstName"
            (reader.currentEntrySize ?: 0L) > MAX_MANIFEST_BYTES ->
                "Manifest entry too large: ${reader.currentEntrySize} bytes"
            else -> null
        }
        if (problem != null) {
            throw BackupError.InvalidArchive(problem)
        }
        val manifestJson = reader.readCurrentEntryToBuffer().readUtf8()
        return runCatching { Json.decodeFromString(BackupManifest.serializer(), manifestJson) }
            .getOrElse { error ->
                throw BackupError.InvalidArchive("Manifest is not valid JSON: ${error.message}", error)
            }
    }

    private fun validateVersions(manifest: BackupManifest) {
        val error = when {
            manifest.backupFormatVersion != BackupManifest.CURRENT_FORMAT_VERSION ->
                BackupError.FormatMismatch(
                    expected = BackupManifest.CURRENT_FORMAT_VERSION,
                    actual = manifest.backupFormatVersion,
                )
            manifest.moneySurferDbVersion != MONEY_SURFER_DB_VERSION ->
                BackupError.SchemaMismatch(
                    expected = MONEY_SURFER_DB_VERSION,
                    actual = manifest.moneySurferDbVersion,
                )
            else -> null
        }
        if (error != null) {
            throw error
        }
    }

    private fun validateManifest(manifest: BackupManifest) {
        validateVersions(manifest)
        val error = when {
            BackupManifest.MAIN_DB_ENTRY_NAME !in manifest.files ->
                BackupError.MissingFile(BackupManifest.MAIN_DB_ENTRY_NAME)
            BackupManifest.DATASTORE_ENTRY_NAME !in manifest.files ->
                BackupError.MissingFile(BackupManifest.DATASTORE_ENTRY_NAME)
            else -> null
        }
        if (error != null) {
            throw error
        }
    }

    /** Reads the single entry named [name], skipping unknown entries. */
    private fun readEntry(reader: ZipStoredReader, name: String): Buffer {
        while (true) {
            val entryName = reader.nextEntryName() ?: break
            if (entryName == name) {
                return reader.readCurrentEntryToBuffer()
            }
            blackholeSink().buffer().use { reader.readCurrentEntryTo(it) }
        }
        throw BackupError.MissingFile(name)
    }

    private fun extractRemainingEntries(
        reader: ZipStoredReader,
        mainDbTemp: Path,
        dataStoreTemp: Path,
    ) {
        var sawMainDb = false
        var sawDataStore = false
        while (true) {
            val name = reader.nextEntryName() ?: break
            when (name) {
                BackupManifest.MAIN_DB_ENTRY_NAME -> {
                    sawMainDb = true
                    writeEntryTo(reader, mainDbTemp)
                }
                BackupManifest.DATASTORE_ENTRY_NAME -> {
                    sawDataStore = true
                    writeEntryTo(reader, dataStoreTemp)
                }
                else -> {
                    // Unknown entries are ignored — gives us forward-compat slack.
                    blackholeSink().buffer().use { reader.readCurrentEntryTo(it) }
                }
            }
        }
        if (!sawMainDb) throw BackupError.MissingFile(BackupManifest.MAIN_DB_ENTRY_NAME)
        if (!sawDataStore) throw BackupError.MissingFile(BackupManifest.DATASTORE_ENTRY_NAME)
    }

    private fun writeEntryTo(reader: ZipStoredReader, target: Path) {
        target.parent?.let { fs.createDirectories(it) }
        fs.deleteIfExists(target)
        fs.sink(target).buffer().use { reader.readCurrentEntryTo(it) }
    }

    private fun rollback(target: Path, backup: Path) {
        if (fs.exists(backup)) {
            runCatching { fs.deleteIfExists(target) }
            runCatching { fs.atomicMove(backup, target) }
        }
    }
}

private fun Path.appendSuffix(suffix: String): Path {
    val parent = parent ?: error("Backup path must be absolute: $this")
    return parent / "$name$suffix"
}

private const val SUFFIX_RESTORE_TMP = ".restore.tmp"
private const val SUFFIX_PRE_IMPORT = ".preimport.bak"
private const val SUFFIX_WAL = "-wal"
private const val SUFFIX_SHM = "-shm"

/** A manifest is a small JSON object; anything bigger is hostile or corrupt. */
private const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
