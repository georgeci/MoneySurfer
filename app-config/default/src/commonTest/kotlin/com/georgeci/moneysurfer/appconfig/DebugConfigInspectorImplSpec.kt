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

private class Env(
    debugActive: Boolean = true,
    keyGroups: List<ConfigKeyGroup> = listOf(TestKeyGroup()),
) {
    val debug = FakeDebugConfigSource(isActive = debugActive)
    private val local = FakeLocalConfigSource()
    val config = LayeredConfig(
        layers = listOf(debug, local, FakeRemoteGlobalConfigSource(), BuildConfigSource { put(FLAG, true) }),
        local = local,
        failFastOnEarlySnapshot = false,
    )
    val inspector = DebugConfigInspectorImpl(
        config = config,
        registry = ConfigRegistry(keyGroups),
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

    "an override the codec can no longer read is still clearable per row" {
        runTest {
            // A codec whose wire format changed between builds leaves values like this behind. They
            // do not win, so deriving `overridden` from the winning layer left the row with no clear
            // action and "Reset all" — which drops every other override — as the only way out.
            val env = Env()
            env.config.hydrate()
            env.debug.override(FLAG, "perhaps")

            val row = env.inspector.rows.first().single { it.name == "panel.flag" }

            row.winner shouldBe "Build"
            row.overridden shouldBe true

            env.inspector.clearOverride("panel.flag")
            env.inspector.rows.first().single { it.name == "panel.flag" }.overridden shouldBe false
        }
    }

    "host-identity keys are flagged so the panel can say they are not ordinary flags" {
        runTest {
            val env = Env(keyGroups = listOf(TestKeyGroup(), HostConfigKeyGroup()))
            env.config.hydrate()

            val rows = env.inspector.rows.first()

            rows.single { it.name == HostConfigKeys.isOffline.name }.hostOwned shouldBe true
            rows.single { it.name == "panel.flag" }.hostOwned shouldBe false
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
