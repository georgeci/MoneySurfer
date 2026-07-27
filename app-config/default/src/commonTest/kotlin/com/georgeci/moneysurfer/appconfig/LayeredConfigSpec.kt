package com.georgeci.moneysurfer.appconfig

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private val KILL_SWITCH: ConfigKey<Boolean> =
    ConfigKey.bool("test.kill_switch", default = true, remoteOverridable = true)

private val HOST_FACT: ConfigKey<Boolean> = ConfigKey.bool("test.host_fact", default = false)

private val USER_SETTING: SettingKey<Boolean> =
    SettingKey.bool("test.user_setting", default = true, sync = true)

/**
 * Builds the production layer order over fakes.
 *
 * On [TestScope] so the shared `changes` collection runs on `backgroundScope` — it is started
 * eagerly and never completes, so a scope `runTest` does not tear down would hang the test.
 */
private fun TestScope.config(
    debug: DebugConfigSource = FakeDebugConfigSource(isActive = false),
    local: LocalConfigSource = FakeLocalConfigSource(),
    remote: RemoteGlobalConfigSource = FakeRemoteGlobalConfigSource(),
    build: BuildConfigSource = BuildConfigSource { },
    failFastOnEarlySnapshot: Boolean = false,
): LayeredConfig = LayeredConfig(
    layers = listOf(debug, local, remote, build),
    local = local,
    scope = backgroundScope,
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
            // The refused value is still reported per layer: the debug panel is the only tool for
            // diagnosing remote config, and "the server sent nothing" and "the server sent
            // something we ignored" are different bugs.
            engine.resolve(HOST_FACT).perLayer[ConfigLayer.RemoteGlobal] shouldBe LayerValue.Present(true)
            engine.resolve(HOST_FACT).winner shouldBe ConfigLayer.Build
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
                scope = backgroundScope,
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

    "a layer that cannot be hydrated degrades instead of taking startup down" {
        runTest {
            // `hydrate()` is the first suspend call the startup coroutine awaits, so a throw here
            // would kill it before a start route exists — a crash loop on a truncated file.
            val engine = LayeredConfig(
                layers = listOf(
                    UnreadableConfigSource(ConfigLayer.Debug),
                    FakeLocalConfigSource(),
                    BuildConfigSource { put(HOST_FACT, true) },
                ),
                local = FakeLocalConfigSource(),
                scope = backgroundScope,
                failFastOnEarlySnapshot = true,
            )

            engine.hydrate()

            engine.degradedLayers shouldBe setOf(ConfigLayer.Debug)
            // Still usable: the surviving layers resolve, and snapshot does not trip the debug guard.
            engine.snapshot(HOST_FACT) shouldBe true
        }
    }

    "the layers that can be read are still warmed when another fails" {
        runTest {
            val engine = LayeredConfig(
                layers = listOf(
                    UnreadableConfigSource(ConfigLayer.Debug),
                    FakeLocalConfigSource(mapOf(USER_SETTING.name to "false")),
                    BuildConfigSource { },
                ),
                local = FakeLocalConfigSource(),
                scope = backgroundScope,
                failFastOnEarlySnapshot = false,
            )

            engine.hydrate()

            // One bad file must not cost the user their stored settings.
            engine.snapshot(USER_SETTING) shouldBe false
        }
    }

    "a source that reports itself degraded is named without throwing anywhere" {
        runTest {
            // How the DataStore-backed layers actually fail: `hydrate()` and `changes` both succeed
            // (they must — the startup coroutine has no route to fall back to) and the source says
            // it could not be read. Reads therefore have to work too, which is what the old
            // hydrate-only mechanism never covered.
            val unreadable = SelfReportingDegradedSource(
                layer = ConfigLayer.Local,
                values = mapOf(USER_SETTING.name to "false"),
            )
            val engine = LayeredConfig(
                layers = listOf(FakeDebugConfigSource(), unreadable, BuildConfigSource { }),
                local = FakeLocalConfigSource(),
                scope = backgroundScope,
                failFastOnEarlySnapshot = true,
            )
            engine.hydrate()

            engine.degradedLayers shouldBe setOf(ConfigLayer.Local)
            // The stored `false` is unreachable while the store is broken, so the default stands in.
            engine.snapshot(USER_SETTING) shouldBe true
            engine.observe(USER_SETTING).first() shouldBe true
        }
    }

    "a degraded layer stops being reported once its store recovers" {
        runTest {
            // The old mechanism latched the set inside `hydrate()`, which returns early forever
            // after — so the panel kept flagging a layer that had been serving correct values for
            // hours.
            val unreadable = SelfReportingDegradedSource(
                layer = ConfigLayer.Local,
                values = mapOf(USER_SETTING.name to "false"),
            )
            val engine = LayeredConfig(
                layers = listOf(FakeDebugConfigSource(), unreadable, BuildConfigSource { }),
                local = FakeLocalConfigSource(),
                scope = backgroundScope,
                failFastOnEarlySnapshot = true,
            )
            engine.hydrate()

            unreadable.recover()

            engine.degradedLayers.shouldBeEmpty()
            engine.snapshot(USER_SETTING) shouldBe false
        }
    }

    "nothing is degraded on the happy path" {
        runTest {
            val engine = config()
            engine.hydrate()

            engine.degradedLayers.shouldBeEmpty()
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
