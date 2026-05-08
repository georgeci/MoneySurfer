package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.buffer
import org.koin.core.annotation.Single

@Single(binds = [BackupExporter::class])
class BackupExporterImpl(
    private val database: MoneySurferDatabase,
    private val locator: BackupStorageLocator,
    private val appInfo: AppInfo,
) : BackupExporter {

    private val mutex = Mutex()
    private val fs: FileSystem = FileSystem.SYSTEM

    override suspend fun exportTo(sink: BufferedSink): Result<BackupManifest> = mutex.withLock {
        runCatching {
            checkpointMainDatabase()
            val manifest = buildManifest()
            val zip = ZipStoredWriter(sink)
            zip.writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, manifestSource(manifest))
            fs.source(locator.moneySurferDbFile()).buffer().use {
                zip.writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, it)
            }
            fs.source(locator.dataStoreFile()).buffer().use {
                zip.writeEntry(BackupManifest.DATASTORE_ENTRY_NAME, it)
            }
            zip.finish()
            manifest
        }.recoverCatching { error ->
            throw if (error is BackupError) error else BackupError.Io(error)
        }
    }

    private suspend fun checkpointMainDatabase() {
        // wal_checkpoint(TRUNCATE) needs exclusive access to truncate the WAL,
        // so we run it through the writer connection (which serialises against
        // ongoing reads / writes) rather than a reader.
        database.useWriterConnection { connection ->
            connection.usePrepared(PRAGMA_WAL_CHECKPOINT) { statement ->
                statement.step()
            }
        }
    }

    private fun buildManifest(): BackupManifest = BackupManifest(
        backupFormatVersion = BackupManifest.CURRENT_FORMAT_VERSION,
        moneySurferDbVersion = MONEY_SURFER_DB_VERSION,
        appVersion = appInfo.version,
        appVersionCode = appInfo.versionCode,
        sourcePlatform = locator.platformName,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        files = listOf(
            BackupManifest.MAIN_DB_ENTRY_NAME,
            BackupManifest.DATASTORE_ENTRY_NAME,
        ),
    )

    private fun manifestSource(manifest: BackupManifest): Buffer {
        val json = Json.encodeToString(BackupManifest.serializer(), manifest)
        return Buffer().apply { writeUtf8(json) }
    }
}

private const val PRAGMA_WAL_CHECKPOINT = "PRAGMA wal_checkpoint(TRUNCATE)"

/** Mirror of [MoneySurferDatabase] `@Database(version = N)` — bump in lockstep. */
private const val MONEY_SURFER_DB_VERSION = 20
