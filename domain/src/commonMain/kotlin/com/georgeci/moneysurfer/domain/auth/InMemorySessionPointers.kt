package com.georgeci.moneysurfer.domain.auth

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Volatile, RAM-only session store — does not persist across restarts. The production
 * Android/iOS/JVM app uses the DataStore-backed `SessionPointersImpl` from `:data-local`. This
 * class exists for unit tests and any non-persisting environment (REPL, scripts) that wants a
 * working session graph without dragging DataStore in.
 *
 * Implements both halves so one instance can serve a reader and a lifecycle use case in the same
 * test.
 */
class InMemorySessionPointers(
    currentUserId: UserId? = null,
    currentWorkspaceId: WorkspaceId? = null,
    currentFirebaseUid: String? = null,
    hasUsedDemo: Boolean = false,
) : SessionPointers, SessionMutator {

    private val userIdState = MutableStateFlow(currentUserId)
    private val workspaceIdState = MutableStateFlow(currentWorkspaceId)
    private val firebaseUidState = MutableStateFlow(currentFirebaseUid)
    private val hasUsedDemoState = MutableStateFlow(hasUsedDemo)

    override val currentUserId: Flow<UserId?> = userIdState
    override val currentWorkspaceId: Flow<WorkspaceId?> = workspaceIdState
    override val currentFirebaseUid: Flow<String?> = firebaseUidState
    override val hasUsedDemo: Flow<Boolean> = hasUsedDemoState

    override suspend fun setCurrentUser(id: UserId?) {
        userIdState.value = id
    }

    override suspend fun setCurrentWorkspace(id: WorkspaceId?) {
        workspaceIdState.value = id
    }

    override suspend fun setFirebaseUid(uid: String?) {
        firebaseUidState.value = uid
    }

    override suspend fun setHasUsedDemo(value: Boolean) {
        hasUsedDemoState.value = value
    }

    override suspend fun clearSession() {
        userIdState.value = null
        workspaceIdState.value = null
        firebaseUidState.value = null
    }
}
