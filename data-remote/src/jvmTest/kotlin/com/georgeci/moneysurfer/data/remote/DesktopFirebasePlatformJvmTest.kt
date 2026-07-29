package com.georgeci.moneysurfer.data.remote

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val WRITER_COUNT = 32
private const val JOIN_TIMEOUT_SECONDS = 30L
private const val PAYLOAD_KEYS = 400
private const val OBSERVATION_ROUNDS = 300

/**
 * This is the store the Firebase java-sdk uses in place of Android's `SharedPreferences`.
 * It holds the installation id and refresh tokens, so losing it silently re-registers the
 * desktop client; failing to parse it on load throws out of `initializeDesktopFirebase()`
 * before the window ever opens. Both failure modes are file-level, which is what these
 * specs pin.
 */
class DesktopFirebasePlatformJvmTest : StringSpec({

    fun storeFile(): File = File.createTempFile("firebase", ".properties").also { it.delete() }

    "a stored value reads back" {
        val platform = DesktopFirebasePlatform(storeFile())

        platform.store("fire-global", "installation-id")

        platform.retrieve("fire-global") shouldBe "installation-id"
    }

    "an absent key reads back as null rather than throwing" {
        DesktopFirebasePlatform(storeFile()).retrieve("never-written").shouldBeNull()
    }

    "values survive the process — a fresh instance reloads them from disk" {
        val file = storeFile()
        DesktopFirebasePlatform(file).store("last-used-date", "2026-07-29")

        // A second instance stands in for the next app launch: same file, new object.
        DesktopFirebasePlatform(file).retrieve("last-used-date") shouldBe "2026-07-29"
    }

    "a cleared key is gone from disk, not just from memory" {
        val file = storeFile()
        val platform = DesktopFirebasePlatform(file)
        platform.store("fire-global", "installation-id")

        platform.clear("fire-global")

        DesktopFirebasePlatform(file).retrieve("fire-global").shouldBeNull()
    }

    "the temp file used for the atomic rename is not left behind" {
        val file = storeFile()

        DesktopFirebasePlatform(file).store("fire-global", "installation-id")

        File(file.parentFile, "${file.name}.tmp").exists().shouldBeFalse()
        file.exists().shouldBeTrue()
    }

    "concurrent writers keep every key" {
        val file = storeFile()
        val platform = DesktopFirebasePlatform(file)
        val pool = Executors.newFixedThreadPool(WRITER_COUNT)
        val startTogether = CountDownLatch(1)

        repeat(WRITER_COUNT) { index ->
            pool.submit {
                startTogether.await()
                platform.store("key-$index", "value-$index")
            }
        }
        startTogether.countDown()
        pool.shutdown()
        pool.awaitTermination(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).shouldBeTrue()

        // Read the file the way the next launch would, rather than trusting the in-memory copy.
        val reloaded = Properties().apply { file.inputStream().use(::load) }
        reloaded.size shouldBe WRITER_COUNT
        repeat(WRITER_COUNT) { index ->
            reloaded.getProperty("key-$index") shouldBe "value-$index"
        }
    }

    // Regression for the torn-write fix. Writing straight into the store truncates it to zero
    // and refills it, so anyone reading during that window — in production, the next launch
    // after a crash mid-write — sees a partial file and silently loses the installation id.
    // Serializing to a temp file and renaming means a reader only ever observes the whole
    // previous file or the whole new one. Against the truncating implementation this spec
    // fails; it is the property, not the interleaving, that is being pinned.
    "a concurrent reader never observes a partially written store" {
        val file = storeFile()
        val platform = DesktopFirebasePlatform(file)
        // A payload big enough that serialization is not instantaneous, widening the window
        // a truncating writer would leave open.
        repeat(PAYLOAD_KEYS) { platform.store("seed-$it", "value-$it") }

        val partialReads = AtomicInteger(0)
        val readerDone = CountDownLatch(1)
        val reader = Thread {
            repeat(OBSERVATION_ROUNDS) {
                val seen = runCatching {
                    Properties().apply { file.inputStream().use(::load) }.size
                }.getOrElse { 0 }
                if (seen < PAYLOAD_KEYS) partialReads.incrementAndGet()
            }
            readerDone.countDown()
        }

        reader.start()
        repeat(OBSERVATION_ROUNDS) { platform.store("churn", "round-$it") }
        readerDone.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).shouldBeTrue()

        partialReads.get() shouldBe 0
    }

    "the database path is a file whose parent exists, not a directory" {
        val file = storeFile()
        // Creating the returned path as a directory is what made Firestore's SQLite layer fail
        // with SQLITE_CANTOPEN and panic its async queue on the first read.
        val dbPath = DesktopFirebasePlatform(file).getDatabasePath("firestore.default")

        dbPath.isDirectory.shouldBeFalse()
        dbPath.parentFile.isDirectory.shouldBeTrue()
    }
})
