package com.georgeci.moneysurfer.data.backup

import androidx.room.Room
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files

/**
 * Test locator that points at files under a caller-owned directory. The same
 * shape as the production locator, but with paths the test controls so we
 * can wipe state between cases without touching `tmpdir/.moneysurfer`.
 */
internal class TestBackupStorageLocator(
    private val rootDir: Path,
    override val platformName: String = "test",
) : BackupStorageLocator {
    init {
        FileSystem.SYSTEM.createDirectories(rootDir)
    }

    override fun moneySurferDbFile(): Path = rootDir / "moneysurfer.db"
    override fun syncDbFile(): Path = rootDir / "moneysurfer_sync.db"
    override fun dataStoreFile(): Path = rootDir / "moneysurfer_settings.preferences_pb"
}

internal fun newTempDir(prefix: String = "backup-test"): Path =
    Files.createTempDirectory(prefix).toAbsolutePath().toString().toPath()

internal fun deleteRecursively(root: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(root)) return
    fs.listOrNull(root)?.forEach { child ->
        val md = fs.metadata(child)
        if (md.isDirectory) deleteRecursively(child) else fs.deleteIfExists(child)
    }
    fs.deleteIfExists(root)
}

internal fun openDatabaseAt(path: Path): MoneySurferDatabase =
    getRoomDatabase(Room.databaseBuilder<MoneySurferDatabase>(path.toString()))

internal val testAppInfo = AppInfo(version = "1.2.3", versionCode = 42)

/**
 * Hand-built archive used by importer error-path tests — bypasses
 * [BackupExporterImpl] entirely so we can craft both well-formed and malformed
 * inputs.
 */
internal fun buildArchive(
    manifestJson: String,
    mainDbBytes: ByteArray = ByteArray(16) { it.toByte() },
    dataStoreBytes: ByteArray = ByteArray(8) { (it + 100).toByte() },
    includeMainDb: Boolean = true,
    includeDataStore: Boolean = true,
): Buffer {
    val sink = Buffer()
    val writer = ZipStoredWriter(sink)
    writer.writeEntry(BackupManifest.MANIFEST_ENTRY_NAME, Buffer().apply { writeUtf8(manifestJson) })
    if (includeMainDb) {
        writer.writeEntry(BackupManifest.MAIN_DB_ENTRY_NAME, Buffer().apply { write(mainDbBytes) })
    }
    if (includeDataStore) {
        writer.writeEntry(
            BackupManifest.DATASTORE_ENTRY_NAME,
            Buffer().apply { write(dataStoreBytes) },
        )
    }
    writer.finish()
    return sink
}

internal fun manifestJson(
    backupFormatVersion: Int = BackupManifest.CURRENT_FORMAT_VERSION,
    moneySurferDbVersion: Int = 20,
    files: List<String> = listOf(
        BackupManifest.MAIN_DB_ENTRY_NAME,
        BackupManifest.DATASTORE_ENTRY_NAME,
    ),
): String = Json.encodeToString(
    BackupManifest.serializer(),
    BackupManifest(
        backupFormatVersion = backupFormatVersion,
        moneySurferDbVersion = moneySurferDbVersion,
        appVersion = testAppInfo.version,
        appVersionCode = testAppInfo.versionCode,
        sourcePlatform = "test",
        createdAtEpochMillis = 1_700_000_000_000L,
        files = files,
    ),
)
