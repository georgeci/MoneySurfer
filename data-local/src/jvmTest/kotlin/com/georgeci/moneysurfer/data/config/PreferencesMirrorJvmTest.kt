package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException

private val KEY = stringPreferencesKey("sync.remote_enabled")

private fun preferencesOf(value: String): Preferences =
    mutablePreferencesOf().apply { this[KEY] = value }

/**
 * A `DataStore` whose reads and writes can be held open, so the interleaving of the mirror's shared
 * collection and [PreferencesMirror.edit] is deterministic rather than a race the test would have to
 * hope for.
 *
 * An emission captures its value *before* parking on [gate], which is what makes it stale: it
 * carries what the store held when it was produced and is delivered after a later write has already
 * committed. [subscriptions] counts collections of `data`, which is what proves the mirror keeps one
 * of them rather than one per observer.
 */
private class GatedDataStore(initial: Preferences) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    /** Set to hold the emission being delivered open until it is completed. */
    var gate: CompletableDeferred<Unit>? = null

    /** Set to hold the next write open until it is completed. */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Set to make `data` fail the way a store with an unreadable file does. Writes still work. */
    var failReads: Boolean = false

    var subscriptions: Int = 0
        private set

    override val data: Flow<Preferences> = flow {
        if (failReads) throw IOException("unreadable")
        state.collect { captured ->
            gate?.await()
            emit(captured)
        }
    }.onStart { subscriptions++ }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeGate?.await()
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * The ordering guarantees the mirror owes the snapshot `peek` reads. The RemoteGlobal layer is what
 * makes them reachable: it writes on every foreground return, while startup hydration may still be
 * in flight — see `RemoteConfigMirrorImpl`.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent(), to pin the interleavings exactly
class PreferencesMirrorJvmTest : StringSpec({

    "one collection of the store serves every observer" {
        runTest {
            // The whole point of sharing: `Config.observe` puts roughly ten collectors on this flow
            // from the Settings screen alone, and each used to open its own `dataStore.data`.
            val store = GatedDataStore(preferencesOf("stored"))
            val mirror = PreferencesMirror(store, backgroundScope)

            repeat(5) { mirror.changes.first() }

            store.subscriptions shouldBe 1
        }
    }

    "a write waits for the one snapshot writer rather than publishing its own value" {
        runTest {
            // The bug this closes: `edit` used to assign the snapshot itself, so the write path and
            // the collection wrote the same field with no ordering between them. A collection
            // holding a pre-write emission would then land *after* the write and put the previous
            // value back — a synchronous `peek` reading state the store no longer holds, until the
            // next emission corrected it. Returning only once the single writer has published the
            // committed value is what removes the window; the emission below is still parked, so an
            // `edit` that published its own value would already be finished here.
            val store = GatedDataStore(preferencesOf("old"))
            store.gate = CompletableDeferred()
            val mirror = PreferencesMirror(store, backgroundScope)
            runCurrent() // the collection captured pre-write state and parked on the gate

            val writing = launch { mirror.edit { it[KEY] = "new" } }
            runCurrent()

            writing.isActive shouldBe true
            // Nothing has been published yet: the pre-write emission is exactly what is queued.
            mirror.raw(KEY.name) shouldBe null

            store.gate?.complete(Unit)
            writing.join()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a second write sees the first one's value rather than the state it replaced" {
        runTest {
            val store = GatedDataStore(emptyPreferences())
            val mirror = PreferencesMirror(store, backgroundScope)
            mirror.hydrate()

            mirror.edit { it[KEY] = "one" }
            mirror.edit { it[KEY] = mirror.raw(KEY.name) + "-two" }

            mirror.raw(KEY.name) shouldBe "one-two"
        }
    }

    "a write is readable synchronously the moment edit returns" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(emptyPreferences()), backgroundScope)

            mirror.edit { it[KEY] = "new" }

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a write that changes nothing still returns rather than waiting for an emission it will not get" {
        runTest {
            // DataStore skips the write when the transform produces an equal value, so there is no
            // emission to wait for — the snapshot already holds what was committed.
            val mirror = PreferencesMirror(GatedDataStore(preferencesOf("stored")), backgroundScope)
            mirror.hydrate()

            mirror.edit { it[KEY] = "stored" }

            mirror.raw(KEY.name) shouldBe "stored"
        }
    }

    "hydrate publishes the stored state" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(preferencesOf("stored")), backgroundScope)

            mirror.hydrate()

            mirror.raw(KEY.name) shouldBe "stored"
        }
    }

    "hydrate does not wait on an in-flight write" {
        runTest {
            // The startup path awaits `hydrate()` before a start route exists, so it must not end
            // up behind someone else's `dataStore.edit` — including its fsync.
            val store = GatedDataStore(preferencesOf("stored"))
            val mirror = PreferencesMirror(store, backgroundScope)
            store.writeGate = CompletableDeferred()

            val writing = launch { mirror.edit { it[KEY] = "new" } }
            runCurrent()

            // Completes while the write above is still parked.
            mirror.hydrate()
            mirror.raw(KEY.name) shouldBe "stored"

            store.writeGate?.complete(Unit)
            writing.join()
            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a write completes even after the change collection has died on an unreadable store" {
        runTest {
            // A read failure ends the one collection, so nothing publishes again. Waiting for a
            // publication here would wedge this write and — through the write lock — every write to
            // the layer after it, for a store the next write proves is perfectly writable.
            val store = GatedDataStore(emptyPreferences())
            store.failReads = true
            val mirror = PreferencesMirror(store, backgroundScope)
            mirror.hydrate()
            mirror.isDegraded shouldBe true

            mirror.edit { it[KEY] = "new" }

            mirror.raw(KEY.name) shouldBe "new"
            // Still degraded: the store is writable, but the layer has no view of it any more.
            mirror.isDegraded shouldBe true
        }
    }

    "an empty store reports nothing rather than failing" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(emptyPreferences()), backgroundScope)

            mirror.hydrate()

            mirror.raw(KEY.name) shouldBe null
            mirror.isDegraded shouldBe false
        }
    }
})
