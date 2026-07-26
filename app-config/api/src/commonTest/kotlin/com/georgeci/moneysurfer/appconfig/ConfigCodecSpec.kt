package com.georgeci.moneysurfer.appconfig

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private enum class Mode { System, Light, Dark }

class ConfigCodecSpec : StringSpec({

    "boolean codec round-trips and accepts either case" {
        BooleanConfigCodec.encode(true) shouldBe "true"
        BooleanConfigCodec.decode("true") shouldBe true
        BooleanConfigCodec.decode("FALSE") shouldBe false
    }

    "an unparseable boolean is undecodable, not false" {
        // `getOrDefault(false)` here would let a typo in a QA override read as a deliberate "off",
        // instead of falling through to the layer that actually holds a value.
        BooleanConfigCodec.decode("yes") shouldBe null
        BooleanConfigCodec.decode("") shouldBe null
    }

    "enum codec resolves by name and rejects anything else" {
        val codec = EnumConfigCodec(Mode.entries)
        codec.encode(Mode.Dark) shouldBe "Dark"
        codec.decode("Light") shouldBe Mode.Light
        codec.decode("dark") shouldBe null
        codec.decode("Sepia") shouldBe null
    }

    "enum codec advertises its choices so the panel can render a picker" {
        EnumConfigCodec(Mode.entries).valueKind shouldBe
            ConfigValueKind.Choice(listOf("System", "Light", "Dark"))
    }

    "SettingKey can never be remote-overridable" {
        // Guards the invariant that the server cannot seed a user setting the user never wrote.
        SettingKey.bool("x", default = true, sync = true).remoteOverridable shouldBe false
        SettingKey.enum("y", Mode.System, sync = false).remoteOverridable shouldBe false
    }
})
