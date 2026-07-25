package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.config.SyncSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [HostCapabilities] with the online defaults. Tests that exercise the offline surface pass
 * `isOffline = true`; the rest of the switches keep their online values unless a test says
 * otherwise, which is what the offline host does too.
 */
data class FakeHostCapabilities(
    override val isOffline: Boolean = false,
    override val signInEmailPassword: Boolean = true,
    override val signInAnonymous: Boolean = true,
    override val signInDemo: Boolean = true,
    override val transferEnabled: Boolean = true,
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

/** [SyncSettings] pinned to one value — `false` by default, matching both shipped hosts. */
class FakeSyncSettings(enabled: Boolean = false) : SyncSettings {
    private val state = MutableStateFlow(enabled)
    override val isEnabled: Flow<Boolean> = state

    suspend fun set(enabled: Boolean) {
        state.value = enabled
    }
}

/** Release-shaped [DebugConfigInspector]: unavailable, no rows, writes rejected. */
object UnavailableDebugConfigInspector : DebugConfigInspector {
    override val isAvailable: Boolean = false
    override val rows: Flow<List<ConfigDebugRow>> = flowOf(emptyList())
    override suspend fun override(name: String, raw: String): Result<Unit> =
        Result.failure(IllegalStateException("debug overrides are unavailable"))

    override suspend fun clearOverride(name: String) = Unit
    override suspend fun resetAll() = Unit
}
