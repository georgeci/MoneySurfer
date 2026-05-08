package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.fixtures.BackupTestHarness
import com.georgeci.moneysurfer.data.backup.fixtures.testAppInfo
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredReader
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem

/** Drives a real Room DB on disk and inspects what the exporter wrote. */
class BackupExporterImplJvmTest : FunSpec({

    lateinit var harness: BackupTestHarness

    beforeEach { harness = BackupTestHarness() }
    afterEach { harness.close() }

    test("export writes manifest first followed by main DB and DataStore entries") {
        val database = harness.database()
        // Force the DB file into existence.
        database.userDao().insert(UserEntity(id = "u-1", displayName = "Alice", isAnon = false))
        // DataStore prefs file: any bytes the caller would normally write.
        FileSystem.SYSTEM.write(harness.locator.dataStoreFile()) {
            writeUtf8("PREFS_V1")
        }

        val sink = Buffer()
        val exporter = BackupExporterImpl(database, harness.locator, testAppInfo)
        val manifest = exporter.exportTo(sink).getOrThrow()

        manifest.appVersion shouldBe testAppInfo.version
        manifest.appVersionCode shouldBe testAppInfo.versionCode
        manifest.sourcePlatform shouldBe harness.locator.platformName
        manifest.backupFormatVersion shouldBe BackupManifest.CURRENT_FORMAT_VERSION
        manifest.files shouldContainExactly listOf(
            BackupManifest.MAIN_DB_ENTRY_NAME,
            BackupManifest.DATASTORE_ENTRY_NAME,
        )

        val reader = ZipStoredReader(sink)
        reader.nextEntryName() shouldBe BackupManifest.MANIFEST_ENTRY_NAME
        val manifestJson = reader.readCurrentEntryToBuffer().readUtf8()
        Json.decodeFromString(BackupManifest.serializer(), manifestJson) shouldBe manifest

        reader.nextEntryName() shouldBe BackupManifest.MAIN_DB_ENTRY_NAME
        val mainDbBuffer = reader.readCurrentEntryToBuffer()
        mainDbBuffer.size shouldBe FileSystem.SYSTEM.metadata(harness.locator.moneySurferDbFile()).size

        reader.nextEntryName() shouldBe BackupManifest.DATASTORE_ENTRY_NAME
        reader.readCurrentEntryToBuffer().readUtf8() shouldBe "PREFS_V1"

        reader.nextEntryName() shouldBe null
    }

    test("export fails when the DataStore prefs file is missing") {
        val database = harness.database()
        database.userDao().insert(UserEntity(id = "u-1", displayName = "Alice", isAnon = false))
        // intentionally do NOT create the DataStore file

        val sink = Buffer()
        val exporter = BackupExporterImpl(database, harness.locator, testAppInfo)
        shouldThrow<BackupError.Io> {
            exporter.exportTo(sink).getOrThrow()
        }
    }
})
