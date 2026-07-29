package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.config.SyncSettings
import com.georgeci.moneysurfer.domain.config.SyncedSettingsSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [HostCapabilities] with the online defaults — `dashboardWidgetStyle` included, which ships off
 * in both hosts, so a test that wants the picker asks for it explicitly. Tests that exercise the offline surface pass
 * `isOffline = true`; the rest of the switches keep their online values unless a test says
 * otherwise, which is what the offline host does too.
 */
data class FakeHostCapabilities(
    override val isOffline: Boolean = false,
    override val signInEmailPassword: Boolean = true,
    override val signInAnonymous: Boolean = true,
    override val signInDemo: Boolean = true,
    override val transferEnabled: Boolean = true,
    override val dashboardWidgetStyle: Boolean = false,
) : HostCapabilities {

    companion object {
        /** The offline host's Build layer: demo-only sign-in, no transfers. */
        fun offline(): FakeHostCapabilities = FakeHostCapabilities(
            isOffline = true,
            signInEmailPassword = false,
            signInAnonymous = false,
            signInDemo = true,
            transferEnabled = false,
        )
    }
}

/** [SyncSettings] pinned to one value — `false` by default, the conservative choice for a test. */
class FakeSyncSettings(enabled: Boolean = false) : SyncSettings {
    private val state = MutableStateFlow(enabled)
    override val isEnabled: Flow<Boolean> = state

    suspend fun set(enabled: Boolean) {
        state.value = enabled
    }
}

/**
 * Counts session-start calls, so a test can assert that a sign-in path clears the logout overlay
 * and reconciles pending pushes *before* it pulls.
 *
 * [failWith] models the real failure mode: the implementation reads Room and writes the outbox —
 * two different databases — and a sign-in must survive either one throwing.
 */
class RecordingSyncedSettingsSession(private val failWith: String? = null) : SyncedSettingsSession {
    var sessionStarts: Int = 0
        private set

    override suspend fun onSessionStart() {
        sessionStarts++
        failWith?.let { error(it) }
    }
}

/** Release-shaped [DebugConfigInspector]: unavailable, no rows, writes rejected. */
object UnavailableDebugConfigInspector : DebugConfigInspector {
    override val isAvailable: Boolean = false
    override val rows: Flow<List<ConfigDebugRow>> = flowOf(emptyList())
    override val degradedLayers: List<String> = emptyList()
    override suspend fun override(name: String, raw: String): Result<Unit> =
        Result.failure(IllegalStateException("debug overrides are unavailable"))

    override suspend fun clearOverride(name: String) = Unit
    override suspend fun resetAll() = Unit
}
