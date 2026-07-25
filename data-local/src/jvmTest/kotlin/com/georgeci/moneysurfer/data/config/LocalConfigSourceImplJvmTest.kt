package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.data.datastore.createDataStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import java.nio.file.Files

private val KEY: SettingKey<Boolean> = SettingKey.bool("ui.onboarding_completed", default = false, sync = false)

/**
 * Runs against a real on-disk DataStore, because the two behaviours that matter here are exactly the
 * ones a fake would paper over: the `config.` namespace, and the warm/cold state of the in-memory
 * mirror `peek` reads.
 */
class LocalConfigSourceImplJvmTest : StringSpec({

    fun newStore(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("ms-config-test")
        return createDataStore { dir.resolve("config.preferences_pb").toString() }
    }

    "peek is absent before hydrate, so a pre-hydration snapshot cannot see this layer" {
        val store = newStore()
        val source = LocalConfigSourceImpl(store)
        source.write(KEY, true)

        // Deliberately not hydrated.
        LocalConfigSourceImpl(store).peek(KEY) shouldBe LayerValue.Absent
        source.peek(KEY) shouldBe LayerValue.Present(true)
    }

    "a written value round-trips after hydrate" {
        val store = newStore()
        LocalConfigSourceImpl(store).write(KEY, true)

        val reopened = LocalConfigSourceImpl(store)
        reopened.hydrate()

        reopened.peek(KEY) shouldBe LayerValue.Present(true)
    }

    "values are namespaced so they cannot clash with the legacy typed preferences" {
        val store = newStore()
        // The pre-engine store wrote `ui.onboarding_completed` as a *boolean* preference. DataStore
        // keys compare by name only, so reading that entry back through a string key would throw on
        // every existing install — the prefix is what keeps the two apart.
        store.edit { it[booleanPreferencesKey(KEY.name)] = true }

        val source = LocalConfigSourceImpl(store)
        source.hydrate()

        source.peek(KEY) shouldBe LayerValue.Absent
        store.data.first()[booleanPreferencesKey(KEY.name)] shouldBe true
    }

    "changes emits the current state and again after a write" {
        val source = LocalConfigSourceImpl(newStore())

        source.changes.first() shouldBe Unit
        source.write(KEY, true)
        source.changes.first() shouldBe Unit
        source.peek(KEY) shouldBe LayerValue.Present(true)
    }

    "reading a key nobody wrote stores nothing" {
        // A read that materialised the default would make a fresh device upload its own defaults
        // before the first remote pull — and win LWW against the user's real settings, because the
        // local write is newer. Nothing in the read path may touch the store.
        val store = newStore()
        val source = LocalConfigSourceImpl(store)

        source.hydrate()
        source.peek(KEY) shouldBe LayerValue.Absent

        store.data.first().asMap().keys.map { it.name } shouldNotContain "config.${KEY.name}"
    }

    "an undecodable value is left on disk rather than overwritten by the fallback" {
        // Absent-in-that-layer is a *resolution* rule, not a repair: silently rewriting the value
        // would destroy the evidence the debug panel exists to show.
        val store = newStore()
        store.edit { it[stringPreferencesKey("config.${KEY.name}")] = "maybe" }
        val source = LocalConfigSourceImpl(store)
        source.hydrate()

        source.peek(KEY) shouldBe LayerValue.Undecodable("maybe")

        store.data.first()[stringPreferencesKey("config.${KEY.name}")] shouldBe "maybe"
    }
})
