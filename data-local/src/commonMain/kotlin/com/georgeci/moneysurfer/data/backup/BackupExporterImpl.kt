package com.georgeci.moneysurfer.data.backup

import androidx.room.useWriterConnection
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.data.db.MONEY_SURFER_DB_VERSION
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupExporter
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.SYSTEM
import okio.buffer
import okio.use
import org.koin.core.annotation.Single

@Single(binds = [BackupExporter::class])
class BackupExporterImpl(
    private val database: MoneySurferDatabase,
    private val locator: BackupStorageLocator,
    private val appInfo: AppInfo,
    private val clock: ClockUseCase,
) : BackupExporter {

    private val mutex = Mutex()
    private val fs: FileSystem = FileSystem.SYSTEM

    override suspend fun exportTo(sink: BufferedSink): Result<BackupManifest> = mutex.withLock {
        try {
            Result.success(withContext(Dispatchers.IO) { runExport(sink) })
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

    private suspend fun runExport(sink: BufferedSink): BackupManifest {
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
        return manifest
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
        createdAtEpochMillis = clock.now().toEpochMilliseconds(),
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
