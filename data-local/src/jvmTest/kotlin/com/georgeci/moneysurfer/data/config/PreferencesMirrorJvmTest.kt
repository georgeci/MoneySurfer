package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

private val KEY = stringPreferencesKey("sync.remote_enabled")

private fun preferencesOf(value: String): Preferences =
    mutablePreferencesOf().apply { this[KEY] = value }

/**
 * A `DataStore` whose reads can be held open, so the interleaving of [PreferencesMirror.hydrate] and
 * [PreferencesMirror.edit] is deterministic rather than a race the test would have to hope for.
 *
 * `data` captures the current value *before* waiting on the gate, which is what makes the read
 * stale: it observes pre-write state and resolves after the write has already published.
 */
private class GatedDataStore(initial: Preferences) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    /** Set to hold the next read open until it is completed. */
    var gate: CompletableDeferred<Unit>? = null

    /** Set to hold the next write open until it is completed. */
    var writeGate: CompletableDeferred<Unit>? = null

    override val data: Flow<Preferences> = flow {
        val captured = state.value
        gate?.await()
        emit(captured)
        emitAll(state)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeGate?.await()
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * The ordering guarantees the mirror owes its three assignment paths. The RemoteGlobal layer is what
 * makes them reachable: it writes on every foreground return, while startup hydration may still be
 * in flight — see `RemoteConfigMirrorImpl`.
 */
class PreferencesMirrorJvmTest : StringSpec({

    "a write that lands during a hydrate read is not overwritten by it" {
        runTest {
            // The failure this pins: hydrate reads "old", a refresh writes "new" and publishes it,
            // then hydrate's older read resolves and puts "old" back — so the layer serves the
            // previous flags for the rest of the session while the store on disk is current.
            val store = GatedDataStore(preferencesOf("old"))
            val mirror = PreferencesMirror(store)
            store.gate = CompletableDeferred()

            val hydrating = launch { mirror.hydrate() }
            yield() // let hydrate capture the pre-write state and park on the gate

            mirror.edit { it[KEY] = "new" }
            store.gate?.complete(Unit)
            hydrating.join()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "hydrate publishes normally when no write intervenes" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(preferencesOf("stored")))

            mirror.hydrate()

            mirror.raw(KEY.name) shouldBe "stored"
        }
    }

    "hydrate does not wait on an in-flight write" {
        runTest {
            // The lock is held only for the assignments, never across a store call. Holding it
            // across the I/O would put the splash behind someone else's `dataStore.edit` — the
            // startup path awaits `hydrate()` before a start route exists.
            val store = GatedDataStore(preferencesOf("stored"))
            val mirror = PreferencesMirror(store)
            store.writeGate = CompletableDeferred()

            val writing = launch { mirror.edit { it[KEY] = "new" } }
            yield()

            // Completes while the write above is still parked; a lock held across the write would
            // hang this call instead.
            mirror.hydrate()
            mirror.raw(KEY.name) shouldBe "stored"

            store.writeGate?.complete(Unit)
            writing.join()
            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a collector that subscribes mid-write does not republish pre-write state" {
        runTest {
            // `dataStore.data` opens with an unlocked read, so a collector starting while a write
            // is in flight can be handed pre-write bytes. Publishing those would undo the write.
            val store = GatedDataStore(preferencesOf("old"))
            val mirror = PreferencesMirror(store)
            store.gate = CompletableDeferred()

            val collecting = launch { mirror.changes.first() }
            yield() // the collector has captured pre-write state and is parked on the gate

            mirror.edit { it[KEY] = "new" }
            store.gate?.complete(Unit)
            collecting.join()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "an empty store reports nothing rather than failing" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(emptyPreferences()))

            mirror.hydrate()

            mirror.raw(KEY.name) shouldBe null
            mirror.isDegraded shouldBe false
        }
    }
})
