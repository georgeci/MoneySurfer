package com.georgeci.moneysurfer.data.backup.fixtures

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.backup.deleteIfExists
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.data.db.MONEY_SURFER_DB_VERSION
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files

/**
 * One-stop wiring for backup-feature tests, mirroring the shape of
 * `IntegrationHarness` in :integration-test. Owns a per-test temp directory,
 * a [TestBackupStorageLocator] over it, and a lazy on-disk Room handle that
 * the test can open / close as needed.
 *
 * Lifecycle: `BackupTestHarness()` then `close()` (or `try/finally`) so the
 * temp directory and any open Room resources are cleaned up.
 */
class BackupTestHarness {
    val tempDir: Path = newTempDir()
    val locator: TestBackupStorageLocator = TestBackupStorageLocator(tempDir)

    private var lazyDb: MoneySurferDatabase? = null

    /**
     * Lazily opens (or returns the previously opened) Room handle at the
     * locator path. The harness tracks the handle and closes it on [close];
     * tests that need an independent handle (for example, to verify state
     * after the importer closed its own copy) should call [openDatabaseAt]
     * directly with the locator's path.
     */
    fun database(): MoneySurferDatabase =
        lazyDb ?: openDatabaseAt(locator.moneySurferDbFile()).also { lazyDb = it }

    fun close() {
        runCatching { lazyDb?.close() }
        deleteRecursively(tempDir)
    }
}

/**
 * Test locator that points at files under a caller-owned directory. Same
 * shape as the production locator, but with paths the test controls.
 */
class TestBackupStorageLocator(
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

fun newTempDir(prefix: String = "backup-test"): Path =
    Files.createTempDirectory(prefix).toAbsolutePath().toString().toPath()

fun deleteRecursively(root: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(root)) return
    fs.listOrNull(root)?.forEach { child ->
        val md = fs.metadata(child)
        if (md.isDirectory) deleteRecursively(child) else fs.deleteIfExists(child)
    }
    fs.deleteIfExists(root)
}

fun openDatabaseAt(path: Path): MoneySurferDatabase =
    getRoomDatabase(Room.databaseBuilder<MoneySurferDatabase>(path.toString()))

val testAppInfo: AppInfo = AppInfo(version = "1.2.3", versionCode = 42)

val testClock: ClockUseCase = ClockUseCase()

/**
 * Bytes of a small but structurally valid SQLite database — the importer now
 * runs `PRAGMA integrity_check` on the restored file, so archives that are
 * meant to import successfully need a real database payload.
 */
fun validSqliteDbBytes(): ByteArray = cachedValidSqliteDb.copyOf()

private val cachedValidSqliteDb: ByteArray by lazy {
    val dir = newTempDir("seed-db")
    try {
        val path = dir / "seed.db"
        val connection = BundledSQLiteDriver().open(path.toString())
        try {
            connection.execSQL("CREATE TABLE seed (id INTEGER PRIMARY KEY, name TEXT)")
            connection.execSQL("INSERT INTO seed (name) VALUES ('ok')")
        } finally {
            connection.close()
        }
        FileSystem.SYSTEM.read(path) { readByteArray() }
    } finally {
        deleteRecursively(dir)
    }
}

/**
 * Hand-built archive used by importer error-path tests — bypasses
 * `BackupExporterImpl` entirely so we can craft both well-formed and
 * malformed inputs.
 */
fun buildArchive(
    manifestJson: String,
    mainDbBytes: ByteArray = validSqliteDbBytes(),
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

fun manifestJson(
    backupFormatVersion: Int = BackupManifest.CURRENT_FORMAT_VERSION,
    moneySurferDbVersion: Int = MONEY_SURFER_DB_VERSION,
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
