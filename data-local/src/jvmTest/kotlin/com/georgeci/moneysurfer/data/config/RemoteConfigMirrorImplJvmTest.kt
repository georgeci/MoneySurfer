package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.datastore.createReplaceOnCorruptionDataStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.nio.file.Files

/**
 * Runs against a real on-disk DataStore: the behaviours that matter here — surviving a process
 * restart, and replacing rather than merging — are exactly the ones an in-memory fake would paper
 * over. The filtering rules on top of this live in `app-config/remote`.
 */
class RemoteConfigMirrorImplJvmTest : StringSpec({

    // Stands in for the application scope the graph binds — see `LocalConfigSourceImplJvmTest`.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    afterSpec { scope.cancel() }

    fun newStore(dir: java.nio.file.Path = Files.createTempDirectory("ms-remote-flags-test")): DataStore<Preferences> =
        createReplaceOnCorruptionDataStore { dir.resolve(REMOTE_FLAGS_FILE_NAME).toString() }

    "a replaced value is readable through raw the moment replaceAll returns" {
        // `replaceAll` does not publish the snapshot itself — it waits for the shared collection to,
        // which is what keeps `RemoteGlobalConfigSource.peek` current right after a refresh.
        val mirror = RemoteConfigMirrorImpl(newStore(), scope)

        mirror.replaceAll(mapOf("sync.remote_enabled" to "false"))

        mirror.raw("sync.remote_enabled") shouldBe "false"
    }

    "a mirrored value survives a cold start" {
        // Reading back through a second wrapper over the *same live store* would be answered from
        // that store's in-memory cache, and would pass even if nothing were ever flushed. So the
        // written file is copied to a fresh location and opened by a store that has never seen the
        // write — a genuine cold start, proving the bytes on disk carry the value.
        val written = Files.createTempDirectory("ms-remote-flags-written")
        RemoteConfigMirrorImpl(newStore(written), scope).replaceAll(mapOf("sync.remote_enabled" to "false"))

        val restarted = Files.createTempDirectory("ms-remote-flags-restarted")
        Files.copy(written.resolve(REMOTE_FLAGS_FILE_NAME), restarted.resolve(REMOTE_FLAGS_FILE_NAME))
        val reopened = RemoteConfigMirrorImpl(newStore(restarted), scope)
        reopened.hydrate()

        // This is what makes an offline launch resolve the last value the server sent.
        reopened.raw("sync.remote_enabled") shouldBe "false"
        reopened.isDegraded shouldBe false
    }

    "replaceAll drops keys the new payload omits" {
        val mirror = RemoteConfigMirrorImpl(newStore(), scope)
        mirror.replaceAll(mapOf("a.flag" to "true", "b.flag" to "true"))

        mirror.replaceAll(mapOf("a.flag" to "false"))

        mirror.raw("a.flag") shouldBe "false"
        // A merge would pin `b.flag` at its last value forever once the owner deleted it.
        mirror.raw("b.flag").shouldBeNull()
    }

    "changes emits the current state and again after a replace" {
        val mirror = RemoteConfigMirrorImpl(newStore(), scope)

        mirror.changes.first() shouldBe Unit
        mirror.replaceAll(mapOf("a.flag" to "true"))
        mirror.changes.first() shouldBe Unit
        mirror.raw("a.flag") shouldBe "true"
    }

    "a corrupt file is replaced instead of failing every read for the life of the install" {
        // Server flags are disposable and refetched, so this store may repair itself — unlike the
        // app's settings file, which also holds the session pointers.
        val dir = Files.createTempDirectory("ms-remote-flags-corrupt-test")
        Files.write(dir.resolve(REMOTE_FLAGS_FILE_NAME), byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5))
        val mirror = RemoteConfigMirrorImpl(newStore(dir), scope)

        mirror.hydrate()

        mirror.isDegraded shouldBe false
        mirror.raw("a.flag").shouldBeNull()
        mirror.replaceAll(mapOf("a.flag" to "true"))
        mirror.raw("a.flag") shouldBe "true"
    }
})
