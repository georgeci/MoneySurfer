package com.georgeci.moneysurfer.appconfig

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private val KILL_SWITCH: ConfigKey<Boolean> =
    ConfigKey.bool("test.kill_switch", default = true, remoteOverridable = true)

private val HOST_FACT: ConfigKey<Boolean> = ConfigKey.bool("test.host_fact", default = false)

private val USER_SETTING: SettingKey<Boolean> =
    SettingKey.bool("test.user_setting", default = true, sync = true)

/** Builds the production layer order over fakes. */
private fun config(
    debug: DebugConfigSource = FakeDebugConfigSource(isActive = false),
    local: LocalConfigSource = FakeLocalConfigSource(),
    remote: RemoteGlobalConfigSource = FakeRemoteGlobalConfigSource(),
    build: BuildConfigSource = BuildConfigSource { },
    failFastOnEarlySnapshot: Boolean = false,
): LayeredConfig = LayeredConfig(
    layers = listOf(debug, local, remote, build),
    local = local,
    failFastOnEarlySnapshot = failFastOnEarlySnapshot,
)

class LayeredConfigSpec : StringSpec({

    "resolution takes the first non-absent layer" {
        runTest {
            val engine = config(
                local = FakeLocalConfigSource(mapOf(USER_SETTING.name to "false")),
                build = BuildConfigSource { put(USER_SETTING, true) },
            )
            engine.hydrate()

            engine.resolve(USER_SETTING).winner shouldBe ConfigLayer.Local
            engine.snapshot(USER_SETTING) shouldBe false
        }
    }

    "Debug beats Local" {
        runTest {
            val engine = config(
                debug = FakeDebugConfigSource(initial = mapOf(USER_SETTING.name to "true")),
                local = FakeLocalConfigSource(mapOf(USER_SETTING.name to "false")),
            )
            engine.hydrate()

            engine.resolve(USER_SETTING).winner shouldBe ConfigLayer.Debug
            engine.snapshot(USER_SETTING) shouldBe true
        }
    }

    "with nothing stored the key default wins and no layer is named" {
        runTest {
            val engine = config()
            engine.hydrate()

            val resolution = engine.resolve(KILL_SWITCH)
            resolution.value shouldBe true
            resolution.winner shouldBe null
        }
    }

    "an undecodable stored value is absent-in-that-layer, so resolution continues downwards" {
        runTest {
            val engine = config(
                local = FakeLocalConfigSource(mapOf(USER_SETTING.name to "maybe")),
                build = BuildConfigSource { put(USER_SETTING, false) },
            )
            engine.hydrate()

            engine.snapshot(USER_SETTING) shouldBe false
            engine.resolve(USER_SETTING).winner shouldBe ConfigLayer.Build
            // Still reported per-layer, because the debug panel must distinguish it from absent.
            engine.resolve(USER_SETTING).perLayer[ConfigLayer.Local] shouldBe LayerValue.Undecodable("maybe")
        }
    }

    "RemoteGlobal serves a remoteOverridable key" {
        runTest {
            val engine = config(remote = FakeRemoteGlobalConfigSource(mapOf(KILL_SWITCH.name to "false")))
            engine.hydrate()

            engine.snapshot(KILL_SWITCH) shouldBe false
        }
    }

    "RemoteGlobal cannot override a host fact even when it names one" {
        runTest {
            // A server able to flip `host.is_offline` would put the online build on the offline
            // start-route branch while its DI graph stays online — the property the opt-in exists
            // to protect. The engine enforces it, so a misbehaving source cannot bypass it.
            val engine = config(
                remote = FakeRemoteGlobalConfigSource(mapOf(HOST_FACT.name to "true")),
                build = BuildConfigSource { put(HOST_FACT, false) },
            )
            engine.hydrate()

            engine.snapshot(HOST_FACT) shouldBe false
            engine.resolve(HOST_FACT).perLayer[ConfigLayer.RemoteGlobal] shouldBe LayerValue.Absent
        }
    }

    "RemoteGlobal cannot seed a user setting the user never wrote" {
        runTest {
            val engine = config(remote = FakeRemoteGlobalConfigSource(mapOf(USER_SETTING.name to "false")))
            engine.hydrate()

            engine.snapshot(USER_SETTING) shouldBe true
        }
    }

    "handle writes to the Local layer and the flow sees it" {
        runTest {
            val engine = config()
            engine.hydrate()
            val pref = engine.handle(USER_SETTING)

            pref.set(false)

            pref.flow.first() shouldBe false
            engine.resolve(USER_SETTING).winner shouldBe ConfigLayer.Local
        }
    }

    "observe re-resolves when a layer changes" {
        runTest {
            val debug = FakeDebugConfigSource()
            val engine = config(debug = debug)
            engine.hydrate()

            engine.observe(USER_SETTING).first() shouldBe true
            debug.override(USER_SETTING, "false")
            engine.observe(USER_SETTING).first() shouldBe false
        }
    }

    "snapshot before hydrate resolves Build and defaults only" {
        runTest {
            val engine = LayeredConfig(
                layers = listOf(
                    FakeStoreConfigSource(
                        ConfigLayer.Local,
                        mapOf(USER_SETTING.name to "false"),
                        warmOnlyAfterHydrate = true,
                    ),
                    BuildConfigSource { put(HOST_FACT, true) },
                ),
                local = FakeLocalConfigSource(),
                failFastOnEarlySnapshot = false,
            )

            engine.snapshot(USER_SETTING) shouldBe true
            engine.snapshot(HOST_FACT) shouldBe true
        }
    }

    "snapshot before hydrate throws in debug builds" {
        runTest {
            val engine = config(failFastOnEarlySnapshot = true)

            // Fails loudly rather than shipping a read that silently sees only the Build layer.
            shouldThrow<IllegalStateException> { engine.snapshot(USER_SETTING) }
        }
    }

    "no read path writes anything" {
        runTest {
            // Materialising `key.default` into Local would make a fresh device upload its own
            // defaults before the first remote pull and win LWW against the user's real settings.
            // Undecodable values must not be repaired either — that is evidence the panel shows.
            val local = FakeLocalConfigSource(mapOf(HOST_FACT.name to "maybe"))
            val engine = config(local = local)

            engine.hydrate()
            engine.snapshot(USER_SETTING) shouldBe true
            engine.observe(USER_SETTING).first() shouldBe true
            engine.resolve(HOST_FACT).value shouldBe false
            engine.handle(USER_SETTING).flow.first() shouldBe true

            local.writes.shouldBeEmpty()
        }
    }

    "hydrate is idempotent" {
        runTest {
            val engine = config()
            engine.hydrate()
            engine.hydrate()

            engine.snapshot(USER_SETTING) shouldBe true
        }
    }
})
