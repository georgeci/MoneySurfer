package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.ZipStoredReader
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.blackholeSink
import okio.buffer
import org.koin.core.annotation.Single

@Single(binds = [BackupImporter::class])
class BackupImporterImpl(
    private val database: MoneySurferDatabase,
    private val locator: BackupStorageLocator,
) : BackupImporter {

    private val mutex = Mutex()
    private val fs: FileSystem = FileSystem.SYSTEM

    override suspend fun importFrom(source: BufferedSource): Result<BackupManifest> = mutex.withLock {
        runCatching { runImport(source) }
            .recoverCatching { error ->
                throw if (error is BackupError) error else BackupError.Io(error)
            }
    }

    private suspend fun runImport(source: BufferedSource): BackupManifest {
        val reader = ZipStoredReader(source)

        val manifest = readManifestEntry(reader)
        validateManifest(manifest)

        val mainDbPath = locator.moneySurferDbFile()
        val dataStorePath = locator.dataStoreFile()
        val syncDbPath = locator.syncDbFile()

        val mainDbTemp = mainDbPath.appendSuffix(SUFFIX_RESTORE_TMP)
        val dataStoreTemp = dataStorePath.appendSuffix(SUFFIX_RESTORE_TMP)

        try {
            extractRemainingEntries(reader, mainDbTemp, dataStoreTemp)
        } catch (error: Throwable) {
            fs.deleteIfExists(mainDbTemp)
            fs.deleteIfExists(dataStoreTemp)
            throw error
        }

        withContext(Dispatchers.IO) {
            database.close()
        }

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
        } catch (error: Throwable) {
            rollback(mainDbPath, mainDbBackup)
            rollback(dataStorePath, dataStoreBackup)
            throw error
        }

        return manifest
    }

    private fun readManifestEntry(reader: ZipStoredReader): BackupManifest {
        val firstName = reader.nextEntryName()
            ?: throw BackupError.InvalidArchive("Empty archive")
        if (firstName != BackupManifest.MANIFEST_ENTRY_NAME) {
            throw BackupError.InvalidArchive(
                "Expected ${BackupManifest.MANIFEST_ENTRY_NAME} as first entry; got $firstName",
            )
        }
        val manifestJson = reader.readCurrentEntryToBuffer().readUtf8()
        return runCatching { Json.decodeFromString(BackupManifest.serializer(), manifestJson) }
            .getOrElse { throw BackupError.InvalidArchive("Manifest is not valid JSON: ${it.message}") }
    }

    private fun validateManifest(manifest: BackupManifest) {
        if (manifest.backupFormatVersion != BackupManifest.CURRENT_FORMAT_VERSION) {
            throw BackupError.FormatMismatch(
                expected = BackupManifest.CURRENT_FORMAT_VERSION,
                actual = manifest.backupFormatVersion,
            )
        }
        if (manifest.moneySurferDbVersion != MONEY_SURFER_DB_VERSION) {
            throw BackupError.SchemaMismatch(
                expected = MONEY_SURFER_DB_VERSION,
                actual = manifest.moneySurferDbVersion,
            )
        }
        if (BackupManifest.MAIN_DB_ENTRY_NAME !in manifest.files) {
            throw BackupError.MissingFile(BackupManifest.MAIN_DB_ENTRY_NAME)
        }
        if (BackupManifest.DATASTORE_ENTRY_NAME !in manifest.files) {
            throw BackupError.MissingFile(BackupManifest.DATASTORE_ENTRY_NAME)
        }
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

/** Mirror of [MoneySurferDatabase] `@Database(version = N)` — bump in lockstep. */
private const val MONEY_SURFER_DB_VERSION = 20
