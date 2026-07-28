package com.georgeci.moneysurfer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

/** Who the navigation drawer's footer names. Both lines are absent until the session resolves. */
data class AppShellIdentity(
    val userName: String? = null,
    val workspaceName: String? = null,
)

/**
 * The identity block the wide-window drawer pins to its bottom (issue #389).
 *
 * Only the drawer presentation collects it, so on a phone or a rail the repositories below are
 * never subscribed. Both tables hold a handful of rows, so the whole-table flows are cheaper than
 * they look and keep the footer reactive to a rename — the suspend `getById` pair would only
 * re-read when a session pointer changed.
 */
@KoinViewModel
class AppShellViewModel(
    session: SessionPointers,
    userRepository: UserRepository,
    workspaceRepository: WorkspaceRepository,
    private val authRemoteRepository: AuthRemoteRepository,
) : ViewModel() {

    val identity: StateFlow<AppShellIdentity> = combine(
        session.currentUserId,
        session.currentWorkspaceId,
        userRepository.getAll(),
        workspaceRepository.getAll(),
    ) { userId, workspaceId, users, workspaces ->
        AppShellIdentity(
            userName = resolveUserName(users.firstOrNull { it.id == userId }),
            workspaceName = workspaces.firstOrNull { it.id == workspaceId }?.name,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AppShellIdentity(),
    )

    /**
     * The local row is a cache and an FK target, so it reliably carries only the display name —
     * the email is the auth provider's to answer for, which is where `SettingsViewModel` reads it
     * too. Null when neither is known (an anonymous session), and the drawer names it instead.
     */
    private fun resolveUserName(user: User?): String? =
        user?.displayName?.takeIf { it.isNotBlank() }
            ?: authRemoteRepository.currentEmail()?.takeIf { it.isNotBlank() }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
