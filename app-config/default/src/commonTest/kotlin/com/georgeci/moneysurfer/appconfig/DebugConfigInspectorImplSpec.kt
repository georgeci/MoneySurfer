package com.georgeci.moneysurfer.appconfig

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private enum class Palette { Brand, Dynamic }

private val FLAG: ConfigKey<Boolean> = ConfigKey.bool("panel.flag", default = false)
private val CHOICE: SettingKey<Palette> = SettingKey.enum("panel.choice", Palette.Brand, sync = false)

private class TestKeyGroup : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = listOf(FLAG, CHOICE)
}

private class Env(debugActive: Boolean = true) {
    val debug = FakeDebugConfigSource(isActive = debugActive)
    private val local = FakeLocalConfigSource()
    val config = LayeredConfig(
        layers = listOf(debug, local, FakeRemoteGlobalConfigSource(), BuildConfigSource { put(FLAG, true) }),
        local = local,
        failFastOnEarlySnapshot = false,
    )
    val inspector = DebugConfigInspectorImpl(
        config = config,
        registry = ConfigRegistry(listOf(TestKeyGroup())),
        debugSource = debug,
    )
}

class DebugConfigInspectorImplSpec : StringSpec({

    "rows list every registered key with its winning layer" {
        runTest {
            val env = Env()
            env.config.hydrate()

            val rows = env.inspector.rows.first()

            rows.map { it.name } shouldBe listOf("panel.choice", "panel.flag")
            rows.single { it.name == "panel.flag" }.winner shouldBe "Build"
            // Nothing holds the choice, so the key's own default wins.
            rows.single { it.name == "panel.choice" }.winner shouldBe "default"
        }
    }

    "each row carries the value kind derived from its codec" {
        runTest {
            val env = Env()
            env.config.hydrate()

            val rows = env.inspector.rows.first()

            rows.single { it.name == "panel.flag" }.kind shouldBe
                com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind.Bool
            rows.single { it.name == "panel.choice" }.kind shouldBe
                com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind.Choice(listOf("Brand", "Dynamic"))
        }
    }

    "an override applies and marks the row as overridden" {
        runTest {
            val env = Env()
            env.config.hydrate()

            env.inspector.override("panel.flag", "false").isSuccess shouldBe true

            val row = env.inspector.rows.first().single { it.name == "panel.flag" }
            row.effectiveValue shouldBe "false"
            row.winner shouldBe "Debug"
            row.overridden shouldBe true
        }
    }

    "clearing an override falls back to the layer underneath" {
        runTest {
            val env = Env()
            env.config.hydrate()
            env.inspector.override("panel.flag", "false")

            env.inspector.clearOverride("panel.flag")

            val row = env.inspector.rows.first().single { it.name == "panel.flag" }
            row.effectiveValue shouldBe "true"
            row.winner shouldBe "Build"
        }
    }

    "an undecodable override is rejected instead of stored" {
        runTest {
            val env = Env()
            env.config.hydrate()

            env.inspector.override("panel.choice", "Sepia").isFailure shouldBe true

            env.inspector.rows.first().single { it.name == "panel.choice" }.winner shouldBe "default"
        }
    }

    "an unknown key name is rejected" {
        runTest {
            val env = Env()
            env.config.hydrate()

            env.inspector.override("panel.nope", "true").isFailure shouldBe true
        }
    }

    "per-layer cells distinguish absent from undecodable" {
        runTest {
            val env = Env()
            env.config.hydrate()
            // Written straight to the store, bypassing the inspector's validation — this is the
            // state a value stored by a newer build leaves behind.
            env.debug.override(CHOICE, "Sepia")

            val cells = env.inspector.rows.first().single { it.name == "panel.choice" }.layers
                .associateBy { it.layer }

            cells.getValue("Debug").undecodable shouldBe true
            cells.getValue("Debug").value shouldBe "Sepia"
            cells.getValue("Local").undecodable shouldBe false
            cells.getValue("Local").value shouldBe null
        }
    }

    "the panel is unavailable when no real debug layer is bound" {
        Env(debugActive = false).inspector.isAvailable shouldBe false
    }
})
