package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.data.datastore.createDataStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import java.nio.file.Files

private val FLAG: ConfigKey<Boolean> = ConfigKey.bool("host.transfer_enabled", default = true)
private val OTHER: ConfigKey<Boolean> = ConfigKey.bool("sync.remote_enabled", default = true)

class DebugConfigSourceImplJvmTest : StringSpec({

    fun newStore(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("ms-debug-overrides-test")
        return createDataStore { dir.resolve(DEBUG_OVERRIDES_FILE_NAME).toString() }
    }

    "a real debug source is active, which is what surfaces the QA panel at all" {
        DebugConfigSourceImpl(newStore()).isActive shouldBe true
    }

    "an override round-trips" {
        val source = DebugConfigSourceImpl(newStore())

        source.override(FLAG, "false")

        source.peek(FLAG) shouldBe LayerValue.Present(false)
    }

    "clearing one override leaves the others alone" {
        val source = DebugConfigSourceImpl(newStore())
        source.override(FLAG, "false")
        source.override(OTHER, "false")

        source.clear(FLAG)

        source.peek(FLAG) shouldBe LayerValue.Absent
        source.peek(OTHER) shouldBe LayerValue.Present(false)
    }

    "reset-all drops every override" {
        val source = DebugConfigSourceImpl(newStore())
        source.override(FLAG, "false")
        source.override(OTHER, "false")

        source.clearAll()

        source.peek(FLAG) shouldBe LayerValue.Absent
        source.peek(OTHER) shouldBe LayerValue.Absent
    }

    "values are stored under the bare key name — the file is dedicated, nothing to namespace against" {
        val store = newStore()
        DebugConfigSourceImpl(store).override(FLAG, "false")

        store.data.first()[stringPreferencesKey(FLAG.name)] shouldBe "false"
    }

    "a raw value the codec rejects is kept verbatim so the panel can show it" {
        // `override` validates before writing, but a codec that changed between builds leaves values
        // like this behind — repairing them here would destroy the evidence the panel exists to show.
        val source = DebugConfigSourceImpl(newStore())
        source.override(FLAG, "perhaps")

        source.peek(FLAG) shouldBe LayerValue.Undecodable("perhaps")
    }

    "a fresh store holds nothing, so the layer resolves absent for every key" {
        val store = newStore()
        val source = DebugConfigSourceImpl(store)
        source.hydrate()

        source.peek(FLAG) shouldBe LayerValue.Absent
        store.data.first().asMap().keys.shouldBeEmpty()
    }
})
