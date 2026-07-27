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

/** A key this layer does not own, standing in for the session pointers on the same file. */
private val OTHER = stringPreferencesKey("session.current_user_id")

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

    /**
     * How many emissions the collection may take. Emissions past it park, which is what lets a test
     * leave a value the collection has already been handed *undelivered* — the stale-value window a
     * self-catching-up fake would otherwise close before the assertion runs.
     */
    private val allowed = MutableStateFlow(Int.MAX_VALUE)
    private var delivered = 0

    /** Set to hold the next write open until it is completed. */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Set to make `data` fail the way a store with an unreadable file does. Writes still work. */
    var failReads: Boolean = false

    var subscriptions: Int = 0
        private set

    /** Parks every further emission. The value already in the collection's hand stays there. */
    fun holdEmissions() {
        allowed.value = delivered
    }

    /** Lets exactly one parked emission through, and parks whatever follows it. */
    fun releaseOne() {
        allowed.value = delivered + 1
    }

    override val data: Flow<Preferences> = flow {
        if (failReads) throw IOException("unreadable")
        state.collect { captured ->
            allowed.first { it > delivered }
            delivered++
            emit(captured)
        }
    }.onStart { subscriptions++ }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeGate?.await()
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    /** A write that does not go through the mirror — `SessionPointersImpl` in production. */
    suspend fun writeElsewhere(value: String) {
        updateData { it.toMutablePreferences().apply { this[OTHER] = value }.toPreferences() }
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

    "a stale first emission cannot be published over a write that beat it" {
        runTest {
            // `dataStore.data` captures its opening value at subscribe time and can deliver it
            // arbitrarily later — after a write has already committed and published over it.
            val store = GatedDataStore(preferencesOf("old"))
            store.holdEmissions()
            val mirror = PreferencesMirror(store, backgroundScope)
            runCurrent() // the collection captured pre-write state and parked

            mirror.edit { it[KEY] = "new" }
            store.releaseOne() // the stale value is delivered now, after the commit
            runCurrent()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a stale emission mid-stream cannot be published over a write either" {
        runTest {
            // The same hazard once the collection is warm, which a first-emission-only guard does
            // not cover: an emission caused by another writer on the same file is in flight when
            // this layer commits its own.
            val store = GatedDataStore(preferencesOf("old"))
            val mirror = PreferencesMirror(store, backgroundScope)
            mirror.hydrate()

            store.holdEmissions()
            store.writeElsewhere("u1")
            runCurrent() // that value is in the collection's hand, parked

            mirror.edit { it[KEY] = "new" }
            store.releaseOne()
            runCurrent()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a write racing another writer on the same file still returns" {
        runTest {
            // The Local layer shares its DataStore with the session pointers, so the store's value
            // can move past a write before the collection ever sees that write — DataStore's cache
            // conflates. Nothing may wait on seeing it published, or the write never returns and
            // the write lock is wedged behind it.
            val store = GatedDataStore(emptyPreferences())
            val mirror = PreferencesMirror(store, backgroundScope)
            mirror.hydrate()
            store.holdEmissions()

            mirror.edit { it[KEY] = "new" }
            // Lands before the collection is dispatched, so the value this layer committed is never
            // handed over at all — the conflating cache only ever offers the newer one.
            store.writeElsewhere("u1")
            store.releaseOne()
            runCurrent()

            mirror.raw(KEY.name) shouldBe "new"
        }
    }

    "a second write sees the first one's value rather than the state it replaced" {
        runTest {
            val mirror = PreferencesMirror(GatedDataStore(emptyPreferences()), backgroundScope)
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

    "a write on a store whose read side has died still lands and clears the degraded flag" {
        runTest {
            val store = GatedDataStore(emptyPreferences())
            store.failReads = true
            val mirror = PreferencesMirror(store, backgroundScope)
            mirror.hydrate()
            mirror.isDegraded shouldBe true

            mirror.edit { it[KEY] = "new" }

            mirror.raw(KEY.name) shouldBe "new"
            mirror.isDegraded shouldBe false
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
