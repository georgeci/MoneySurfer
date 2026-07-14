package com.georgeci.moneysurfer.data.backup

import com.georgeci.moneysurfer.data.backup.zip.ZipStoredReader
import com.georgeci.moneysurfer.data.backup.zip.ZipStoredWriter
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.Buffer

class ZipStoredCodecTest : FunSpec({

    test("writer + reader round-trip preserves entry names and bytes") {
        val sink = Buffer()
        val writer = ZipStoredWriter(sink)
        writer.writeEntry("a.txt", Buffer().apply { writeUtf8("hello") })
        writer.writeEntry("b/c.bin", Buffer().apply { write(byteArrayOf(0, 1, 2, 3, 4, 5)) })
        writer.finish()

        val reader = ZipStoredReader(sink)
        reader.nextEntryName() shouldBe "a.txt"
        reader.readCurrentEntryToBuffer().readUtf8() shouldBe "hello"

        reader.nextEntryName() shouldBe "b/c.bin"
        reader.readCurrentEntryToBuffer().readByteArray() shouldBe byteArrayOf(0, 1, 2, 3, 4, 5)

        reader.nextEntryName() shouldBe null
    }

    test("reader rejects truncated entry body") {
        val sink = Buffer()
        val writer = ZipStoredWriter(sink)
        writer.writeEntry("only.txt", Buffer().apply { writeUtf8("12345678901234567890") })
        writer.finish()

        // LFH (30) + name (8) + body (20) = 58. Take 50 bytes to land mid-body.
        val truncated = Buffer()
        sink.copyTo(truncated, offset = 0, byteCount = 50)

        val reader = ZipStoredReader(truncated)
        reader.nextEntryName() shouldBe "only.txt"
        shouldThrow<BackupError.InvalidArchive> {
            reader.readCurrentEntryToBuffer()
        }
    }

    test("reader rejects unsupported compression method") {
        val sink = Buffer()
        // Hand-craft a local file header with method = 8 (DEFLATE).
        with(sink) {
            writeIntLe(0x04034b50)
            writeShortLe(20)
            writeShortLe(0)
            writeShortLe(8) // DEFLATE — unsupported
            writeShortLe(0)
            writeShortLe(0)
            writeIntLe(0)
            writeIntLe(0)
            writeIntLe(0)
            writeShortLe(1)
            writeShortLe(0)
            writeUtf8("a")
        }

        val reader = ZipStoredReader(sink)
        shouldThrow<BackupError.InvalidArchive> {
            reader.nextEntryName()
        }
    }

    test("manifest JSON survives serialization") {
        val original = BackupManifest(
            backupFormatVersion = 1,
            moneySurferDbVersion = 21,
            appVersion = "9.9.9",
            appVersionCode = 9999,
            sourcePlatform = "android",
            createdAtEpochMillis = 1_700_000_000_000,
            files = listOf("moneysurfer.db", "moneysurfer_settings.preferences_pb"),
        )
        val json = Json.encodeToString(BackupManifest.serializer(), original)
        Json.decodeFromString(BackupManifest.serializer(), json) shouldBe original
    }
})
