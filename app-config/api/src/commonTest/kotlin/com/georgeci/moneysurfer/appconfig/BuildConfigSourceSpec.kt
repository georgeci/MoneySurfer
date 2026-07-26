package com.georgeci.moneysurfer.appconfig

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BuildConfigSourceSpec : StringSpec({

    "a declared key resolves to its typed value" {
        val source = BuildConfigSource {
            put(HostConfigKeys.isOffline, true)
        }

        source.peek(HostConfigKeys.isOffline) shouldBe LayerValue.Present(true)
    }

    "an undeclared key is absent, so resolution falls through to the key default" {
        val source = BuildConfigSource {
            put(HostConfigKeys.isOffline, true)
        }

        source.peek(HostConfigKeys.transferEnabled) shouldBe LayerValue.Absent
    }

    "a declared false is Present, not Absent" {
        // The whole point of the Absent/Present split: `false` is a value the host chose, and it has
        // to beat the key's default rather than be mistaken for "not declared".
        val source = BuildConfigSource {
            put(HostConfigKeys.transferEnabled, false)
        }

        source.peek(HostConfigKeys.transferEnabled) shouldBe LayerValue.Present(false)
    }

    "the layer is always Build" {
        BuildConfigSource { }.layer shouldBe ConfigLayer.Build
    }
})
