package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigValueKind
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaletteSourceCodecTest : StringSpec({

    "round-trips every variant in the pre-engine wire format" {
        listOf(
            PaletteSource.Brand to "BRAND",
            PaletteSource.Dynamic to "DYNAMIC",
            PaletteSource.Preset(AccentSeed.Plum) to "PRESET:Plum",
            PaletteSource.Preset(AccentSeed.Mint) to "PRESET:Mint",
        ).forEach { (value, encoded) ->
            PaletteSourceCodec.encode(value) shouldBe encoded
            PaletteSourceCodec.decode(encoded) shouldBe value
        }
    }

    "an unknown seed is undecodable rather than silently Brand" {
        // The old adapter fell back to a default here, which would overwrite a value written by a
        // newer build instead of letting resolution fall through to the layer below.
        PaletteSourceCodec.decode("PRESET:Chartreuse") shouldBe null
    }

    "a malformed value is undecodable" {
        PaletteSourceCodec.decode("") shouldBe null
        PaletteSourceCodec.decode("brand") shouldBe null
        PaletteSourceCodec.decode("PRESET") shouldBe null
        PaletteSourceCodec.decode("PRESET:") shouldBe null
    }

    "value kind enumerates every choice so the panel never needs free-text entry" {
        val kind = PaletteSourceCodec.valueKind
        kind shouldBe ConfigValueKind.Choice(
            listOf("BRAND", "DYNAMIC") + AccentSeed.entries.map { "PRESET:${it.name}" },
        )
    }
})
