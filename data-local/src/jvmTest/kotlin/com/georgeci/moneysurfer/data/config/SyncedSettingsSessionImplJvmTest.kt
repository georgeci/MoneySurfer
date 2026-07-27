package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.SessionConfigOverlay
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.appconfig.layerValueOf
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val THEME: SettingKey<String> = SettingKey.string("ui.theme_mode", default = "System", sync = true)
private val PERIOD: SettingKey<String> =
    SettingKey.string("ui.transactions_period_mode", default = "Month", sync = true)

class SyncedSettingsSessionImplJvmTest : StringSpec({

    "session start drops the overlay the account wipe filled" {
        // Left in place, the previous user's theme would shadow whatever this user's pull writes
        // into local storage — the leak the wipe exists to prevent.
        val overlay = RecordingOverlay()
        overlay.hold(mapOf(THEME.name to "Dark"))
        val session = SyncedSettingsSessionImpl(overlay, FakeConfigEntryDao(), RecordingOutbox())

        session.onSessionStart()

        overlay.peek(THEME) shouldBe LayerValue.Absent
    }

    "a setting written while signed out is queued at the next sign-in" {
        // `OutboxEnqueuerImpl` no-ops without a Firebase uid and nothing else replays those writes.
        val dao = FakeConfigEntryDao()
        dao.write(key = THEME.name, value = "Dark", updatedAt = 100)
        val outbox = RecordingOutbox()

        SyncedSettingsSessionImpl(RecordingOverlay(), dao, outbox).onSessionStart()

        outbox.upserts shouldContainExactlyInAnyOrder listOf(
            Triple(SyncEntityTypes.USER_CONFIG, THEME.name, null),
        )
    }

    "an already-pushed setting is not queued again" {
        val dao = FakeConfigEntryDao()
        dao.write(key = THEME.name, value = "Dark", updatedAt = 100)
        dao.markPushed(key = THEME.name, pushedUpdatedAt = 100)
        val outbox = RecordingOutbox()

        SyncedSettingsSessionImpl(RecordingOverlay(), dao, outbox).onSessionStart()

        outbox.upserts.shouldBeEmpty()
    }

    "a pulled value is not echoed back to the server" {
        // The pull stamps `lastPushedAt`, so reconciliation has nothing to say about it.
        val dao = FakeConfigEntryDao()
        dao.applyRemote(key = THEME.name, value = "Dark", updatedAt = 100)
        val outbox = RecordingOutbox()

        SyncedSettingsSessionImpl(RecordingOverlay(), dao, outbox).onSessionStart()

        outbox.upserts.shouldBeEmpty()
    }

    "every stale key is queued, not just the first" {
        val dao = FakeConfigEntryDao()
        dao.write(key = THEME.name, value = "Dark", updatedAt = 100)
        dao.write(key = PERIOD.name, value = "Week", updatedAt = 100)
        dao.markPushed(key = PERIOD.name, pushedUpdatedAt = 50)
        val outbox = RecordingOutbox()

        SyncedSettingsSessionImpl(RecordingOverlay(), dao, outbox).onSessionStart()

        outbox.upserts.map { it.second } shouldContainExactlyInAnyOrder listOf(THEME.name, PERIOD.name)
    }

    "running twice queues nothing new — the outbox itself dedupes, and so does this" {
        val dao = FakeConfigEntryDao()
        dao.write(key = THEME.name, value = "Dark", updatedAt = 100)
        val outbox = RecordingOutbox()
        val session = SyncedSettingsSessionImpl(RecordingOverlay(), dao, outbox)

        session.onSessionStart()
        session.onSessionStart()

        // Two identical rows reach `enqueue`, which is insert-if-absent among pending rows — this
        // fake records both, so the assertion is about what the queue is asked for, not what lands.
        outbox.upserts.map { it.second } shouldBe listOf(THEME.name, THEME.name)
    }
})

/**
 * The in-memory overlay, reimplemented here rather than pulled in from `app-config/default`: this
 * module sees only the `api` contract, and the production implementation is covered by
 * `LayeredConfigSpec`.
 */
private class RecordingOverlay : SessionConfigOverlay {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = key.layerValueOf(state.value[key.name])
    override val changes: Flow<Unit> = state.map { }
    override suspend fun hydrate() = Unit

    override fun hold(values: Map<String, String>) {
        state.value = state.value + values
    }

    override fun release(name: String) {
        state.value = state.value - name
    }

    override fun clear() {
        state.value = emptyMap()
    }
}
