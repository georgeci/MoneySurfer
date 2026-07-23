package com.georgeci.moneysurfer.feature.settings

import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.usecase.ListPendingInvitesForCurrentUserUseCase
import com.georgeci.moneysurfer.domain.usecase.LogoutUseCase
import com.georgeci.moneysurfer.domain.usecase.RefreshIncomingInvitesUseCase
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.KoinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
@Suppress("LongParameterList") // Aggregator ViewModel; offline-flag injection edges past the 10-arg threshold.
class SettingsViewModel(
    private val session: SessionPointers,
    private val authRemoteRepository: AuthRemoteRepository,
    private val pendingMutationQueue: PendingMutationQueue,
    private val logoutUseCase: LogoutUseCase,
    private val listPendingInvites: ListPendingInvitesForCurrentUserUseCase,
    private val refreshIncomingInvites: RefreshIncomingInvitesUseCase,
    private val memberRepository: WorkspaceMemberRepository,
    private val uiPreferences: UiPreferences,
    private val syncFeatureFlag: SyncFeatureFlag,
    appInfo: AppInfo,
    offlineBuildFlags: OfflineBuildFlags,
) : MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(
    initialState = SettingsState(
        appVersion = appInfo.version,
        isOffline = offlineBuildFlags.isOffline,
        syncEnabled = syncFeatureFlag.enabled,
    ),
) {

    init {
        loadUserIdentity()
        observePendingCount()
        observeIncomingInvites()
        observeActiveWorkspace()
        observeDynamicColor()
        refreshIncoming()
    }

    private fun loadUserIdentity() {
        updateState {
            copy(
                userEmail = authRemoteRepository.currentEmail(),
                isAnonymousUser = authRemoteRepository.isCurrentUserAnonymous(),
            )
        }
    }

    private fun refreshIncoming() {
        launch(onError = { /* swallow — badge falls back to local cache */ }) {
            refreshIncomingInvites()
        }
    }

    private fun observePendingCount() {
        // Sync-feature flag off → outbox queue is irrelevant to the user (no UI surface
        // can act on it). Skip the subscription so the badge stays at zero and we don't
        // hold a Flow open for nothing.
        if (!syncFeatureFlag.enabled) return
        launch {
            pendingMutationQueue.pendingCount
                .onEach { count -> updateState { copy(pendingMutationsCount = count) } }
                .collect()
        }
    }

    private fun observeIncomingInvites() {
        launch {
            listPendingInvites()
                .onEach { invites -> updateState { copy(pendingInviteCount = invites.size) } }
                .collect()
        }
    }

    private fun observeDynamicColor() {
        launch {
            uiPreferences.paletteSource.flow
                .onEach { source ->
                    updateState { copy(isDynamicColorEnabled = source is PaletteSource.Dynamic) }
                }
                .collect()
        }
    }

    private fun observeActiveWorkspace() {
        launch {
            session.currentWorkspaceId.flow
                .onEach { workspaceId -> updateState { copy(currentWorkspaceId = workspaceId) } }
                .flatMapLatest { workspaceId ->
                    if (workspaceId == null) flowOf(emptyList()) else memberRepository.getByWorkspaceId(workspaceId)
                }
                .onEach { members ->
                    val activeCount = members.count { it.status == WorkspaceMemberStatus.ACTIVE }
                    updateState { copy(activeMemberCount = activeCount) }
                }
                .collect()
        }
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.OnMembersClick -> {
                val workspaceId = currentState.currentWorkspaceId
                if (workspaceId != null) {
                    postSideEffect(SettingsEffect.NavigateToMembers(workspaceId))
                }
            }
            SettingsEvent.OnLogoutClick -> logout()
            else -> navigationEffect(event)?.let(::postSideEffect)
        }
    }

    /** Plain event → destination mapping; stateful events are handled in [onEvent]. */
    private fun navigationEffect(event: SettingsEvent): SettingsEffect? = when (event) {
        SettingsEvent.OnBackClick -> SettingsEffect.NavigateBack
        SettingsEvent.OnChangeWorkspaceClick -> SettingsEffect.NavigateToWorkspaceSelector
        SettingsEvent.OnIncomingInvitesClick -> SettingsEffect.NavigateToIncomingInvites
        SettingsEvent.OnCategoriesClick -> SettingsEffect.NavigateToCategories
        SettingsEvent.OnAppearanceClick -> SettingsEffect.NavigateToAppearance
        SettingsEvent.OnPreferencesClick -> SettingsEffect.NavigateToPreferences
        SettingsEvent.OnSyncClick -> SettingsEffect.NavigateToSync
        SettingsEvent.OnBackupClick -> SettingsEffect.NavigateToBackup
        SettingsEvent.OnCsvBackupClick -> SettingsEffect.NavigateToCsvBackup
        SettingsEvent.OnAboutClick -> SettingsEffect.NavigateToAbout
        SettingsEvent.OnDeleteAccountClick -> SettingsEffect.NavigateToDeleteAccount
        SettingsEvent.OnMembersClick, SettingsEvent.OnLogoutClick -> null
    }

    private fun logout() {
        launch { logoutUseCase() }
    }
}

data class SettingsState(
    val appVersion: String = "1.0.0",
    val userEmail: String? = null,
    val isAnonymousUser: Boolean = false,
    val pendingMutationsCount: Int = 0,
    val pendingInviteCount: Int = 0,
    val currentWorkspaceId: WorkspaceId? = null,
    val activeMemberCount: Int = 0,
    val isDynamicColorEnabled: Boolean = false,
    val isOffline: Boolean = false,
    val syncEnabled: Boolean = false,
) {
    val showProfile: Boolean get() = !isOffline
    val showSyncSection: Boolean get() = !isOffline && syncEnabled
    val showLogout: Boolean get() = !isOffline

    /** Account deletion targets the remote account — offline builds have none (issue #213). */
    val showDeleteAccount: Boolean get() = !isOffline
    val showWorkspaceMembers: Boolean get() = !isOffline
    val showPendingInvites: Boolean get() = !isOffline
}

sealed interface SettingsEvent {
    data object OnBackClick : SettingsEvent
    data object OnChangeWorkspaceClick : SettingsEvent
    data object OnIncomingInvitesClick : SettingsEvent
    data object OnMembersClick : SettingsEvent
    data object OnCategoriesClick : SettingsEvent
    data object OnAppearanceClick : SettingsEvent
    data object OnPreferencesClick : SettingsEvent
    data object OnSyncClick : SettingsEvent
    data object OnBackupClick : SettingsEvent
    data object OnCsvBackupClick : SettingsEvent
    data object OnAboutClick : SettingsEvent
    data object OnLogoutClick : SettingsEvent
    data object OnDeleteAccountClick : SettingsEvent
}

sealed interface SettingsEffect {
    data object NavigateBack : SettingsEffect
    data object NavigateToWorkspaceSelector : SettingsEffect
    data object NavigateToIncomingInvites : SettingsEffect
    data class NavigateToMembers(val workspaceId: WorkspaceId) : SettingsEffect
    data object NavigateToCategories : SettingsEffect
    data object NavigateToAppearance : SettingsEffect
    data object NavigateToPreferences : SettingsEffect
    data object NavigateToSync : SettingsEffect
    data object NavigateToBackup : SettingsEffect
    data object NavigateToCsvBackup : SettingsEffect
    data object NavigateToAbout : SettingsEffect
    data object NavigateToDeleteAccount : SettingsEffect
}
