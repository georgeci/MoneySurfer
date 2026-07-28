package com.georgeci.moneysurfer.appconfig.remote

import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.ConfigRegistry
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

private val KILL_SWITCH: ConfigKey<Boolean> =
    ConfigKey.bool("sync.remote_enabled", default = true, remoteOverridable = true)

/** Host-owned: a server able to flip this would put the online build on the offline route. */
private val HOST_FACT: ConfigKey<Boolean> = ConfigKey.bool("host.is_offline", default = false)

private class TestKeyGroup : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = listOf(KILL_SWITCH, HOST_FACT)
}

/** In-memory stand-in for the DataStore-backed mirror; the real one is covered in `data-local`. */
private class FakeMirror(
    initial: Map<String, String> = emptyMap(),
    private val writeFailure: Throwable? = null,
) : RemoteConfigMirror {
    var values: Map<String, String> = initial
        private set

    private val _changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    /** `false` until [hydrate], mirroring how a real cold, un-hydrated source reports absent. */
    var warm: Boolean = false
        private set

    /** Counts calls, so a test can prove an overlapping refresh was dropped rather than queued. */
    var writeCount: Int = 0
        private set

    override fun raw(name: String): String? = if (warm) values[name] else null
    override val changes: Flow<Unit> = _changes.asSharedFlow()
    override suspend fun hydrate() { warm = true }
    override suspend fun replaceAll(values: Map<String, String>) {
        writeFailure?.let { throw it }
        writeCount++
        val changed = this.values != values
        this.values = values
        warm = true
        // Only on a real change, like the DataStore-backed mirror: it skips a write whose result
        // equals what it already holds, so an unchanged refresh produces no emission.
        if (changed) _changes.tryEmit(Unit)
    }
}

private fun source(
    fetch: RemoteFlagFetch,
    mirror: RemoteConfigMirror = FakeMirror(),
): RemoteGlobalConfigSourceImpl = RemoteGlobalConfigSourceImpl(
    document = RemoteFlagDocument { fetch },
    registry = ConfigRegistry(listOf(TestKeyGroup())),
    mirror = mirror,
)

class RemoteGlobalConfigSourceImplSpec : StringSpec({

    "a remoteOverridable flag is mirrored and served" {
        runTest {
            val mirror = FakeMirror()
            val layer = source(RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "false")), mirror)

            layer.refresh()

            mirror.values shouldBe mapOf(KILL_SWITCH.name to "false")
            layer.peek(KILL_SWITCH) shouldBe LayerValue.Present(false)
        }
    }

    "a stringified console boolean decodes whatever its casing" {
        runTest {
            // gitlive decodes every Firestore field through `toString()`, so a boolean typed into
            // the console reaches this layer as "true"/"false" and the owner never has to remember
            // to quote flag values. That conversion belongs to the Firestore reader; what is
            // asserted here is the half this layer owns — that such a value resolves.
            val layer = source(RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "False")))

            layer.refresh()

            layer.peek(KILL_SWITCH) shouldBe LayerValue.Present(false)
        }
    }

    "a key that is not remoteOverridable is ignored and never mirrored" {
        runTest {
            val mirror = FakeMirror()
            val layer = source(RemoteFlagFetch.Read(mapOf(HOST_FACT.name to "true")), mirror)

            layer.refresh()

            // Not merely refused at resolution time: it must not reach local storage at all, or the
            // debug panel would report the RemoteGlobal layer as holding a host fact.
            mirror.values.shouldBeEmpty()
            layer.peek(HOST_FACT) shouldBe LayerValue.Absent
        }
    }

    "an unknown key name is ignored and never mirrored" {
        runTest {
            val mirror = FakeMirror()
            val layer = source(RemoteFlagFetch.Read(mapOf("not.a.key" to "true")), mirror)

            layer.refresh()

            mirror.values.shouldBeEmpty()
        }
    }

    "a value the codec rejects stays visible as undecodable rather than being dropped" {
        runTest {
            // Dropping it at the writer would present the fallback as the server's answer; the
            // debug panel exists to tell those two apart.
            val layer = source(RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "maybe")))

            layer.refresh()

            layer.peek(KILL_SWITCH) shouldBe LayerValue.Undecodable("maybe")
        }
    }

    "a flag removed from the document stops being served" {
        runTest {
            val mirror = FakeMirror(mapOf(KILL_SWITCH.name to "false"))
            mirror.hydrate()
            val layer = source(RemoteFlagFetch.Read(emptyMap()), mirror)

            layer.refresh()

            // Replace, not merge: a retracted kill switch that stayed mirrored would keep sync off
            // on every device that ever saw it.
            mirror.values.shouldBeEmpty()
            layer.peek(KILL_SWITCH) shouldBe LayerValue.Absent
        }
    }

    "an absent document clears the mirror" {
        runTest {
            // `Read(emptyMap())` is also what a missing document produces — the server answered, and
            // its answer is "no overrides".
            val mirror = FakeMirror(mapOf(KILL_SWITCH.name to "false"))
            mirror.hydrate()

            source(RemoteFlagFetch.Read(emptyMap()), mirror).refresh()

            mirror.values.shouldBeEmpty()
        }
    }

    "an unreachable server leaves the mirrored values in place" {
        runTest {
            val mirror = FakeMirror(mapOf(KILL_SWITCH.name to "false"))
            mirror.hydrate()
            val layer = source(RemoteFlagFetch.Unavailable, mirror)

            layer.refresh()

            // The whole point of mirroring: offline must resolve the last values the server sent,
            // not fall through to Build.
            mirror.values shouldBe mapOf(KILL_SWITCH.name to "false")
            layer.peek(KILL_SWITCH) shouldBe LayerValue.Present(false)
        }
    }

    "hydrate serves mirrored values without any fetch" {
        runTest {
            val mirror = FakeMirror(mapOf(KILL_SWITCH.name to "false"))
            val layer = source(RemoteFlagFetch.Unavailable, mirror)

            layer.hydrate()

            // Cold start on a plane: the value is available on the first frame, before `refresh()`
            // has had any chance to return.
            layer.peek(KILL_SWITCH) shouldBe LayerValue.Present(false)
        }
    }

    "peek is absent before hydrate, so a pre-hydration snapshot cannot see this layer" {
        runTest {
            val layer = source(RemoteFlagFetch.Unavailable, FakeMirror(mapOf(KILL_SWITCH.name to "false")))

            layer.peek(KILL_SWITCH) shouldBe LayerValue.Absent
        }
    }

    "an unwritable mirror does not propagate out of refresh" {
        runTest {
            // `refresh()` runs on a fire-and-forget coroutine with no handler above it, so a full
            // disk or a read-only store must cost one stale refresh rather than the whole app.
            val mirror = FakeMirror(
                initial = mapOf(KILL_SWITCH.name to "false"),
                writeFailure = IllegalStateException("disk full"),
            )
            mirror.hydrate()
            val layer = source(RemoteFlagFetch.Read(emptyMap()), mirror)

            shouldNotThrowAny { layer.refresh() }

            layer.peek(KILL_SWITCH) shouldBe LayerValue.Present(false)
        }
    }

    // ── degraded reporting ───────────────────────────────────────────────────

    "a healthy refresh reports the layer as not degraded" {
        runTest {
            val layer = source(RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "false")))

            layer.refresh()

            layer.isDegraded shouldBe false
        }
    }

    "an unreachable server marks the layer degraded" {
        runTest {
            // Without this the debug panel presents weeks-old mirrored flags as the server's
            // current answer — the one distinction `Config.degradedLayers` exists to draw.
            val layer = source(RemoteFlagFetch.Unavailable)

            layer.refresh()

            layer.isDegraded shouldBe true
        }
    }

    "an unwritable mirror marks the layer degraded" {
        runTest {
            val mirror = FakeMirror(writeFailure = IllegalStateException("disk full"))
            val layer = source(RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "false")), mirror)

            layer.refresh()

            // The write is swallowed so the app survives; the degraded flag is what keeps that
            // silence honest.
            layer.isDegraded shouldBe true
        }
    }

    "a recovered server clears the degraded flag" {
        runTest {
            // Live rather than latched, matching every other ConfigSource.
            val mirror = FakeMirror()
            var answer: RemoteFlagFetch = RemoteFlagFetch.Unavailable
            val layer = RemoteGlobalConfigSourceImpl(
                document = { answer },
                registry = ConfigRegistry(listOf(TestKeyGroup())),
                mirror = mirror,
            )

            layer.refresh()
            layer.isDegraded shouldBe true

            answer = RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "false"))
            layer.refresh()

            layer.isDegraded shouldBe false
        }
    }

    // ── overlapping refreshes ────────────────────────────────────────────────

    "a refresh that lands while one is in flight is dropped, not queued" {
        runTest {
            // Each Android Activity recreation — rotation, dark mode, font size, locale — is a
            // foreground return. Queueing them would pay a billed document read for every one,
            // long after the event that asked for it.
            val mirror = FakeMirror()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var fetches = 0
            val layer = RemoteGlobalConfigSourceImpl(
                document = {
                    fetches++
                    started.complete(Unit)
                    release.await()
                    RemoteFlagFetch.Read(mapOf(KILL_SWITCH.name to "false"))
                },
                registry = ConfigRegistry(listOf(TestKeyGroup())),
                mirror = mirror,
            )

            val inFlight = launch { layer.refresh() }
            started.await()
            layer.refresh() // lands mid-flight
            release.complete(Unit)
            inFlight.join()

            fetches shouldBe 1
            mirror.writeCount shouldBe 1
        }
    }

    // ── rejection logging ────────────────────────────────────────────────────

    "an unservable name is still rejected on every refresh, not only the first" {
        runTest {
            // The log is deduplicated per name; the filtering itself must not be.
            val mirror = FakeMirror()
            val layer = source(RemoteFlagFetch.Read(mapOf(HOST_FACT.name to "true")), mirror)

            layer.refresh()
            layer.refresh()

            mirror.values.shouldBeEmpty()
            layer.peek(HOST_FACT) shouldBe LayerValue.Absent
        }
    }
})
