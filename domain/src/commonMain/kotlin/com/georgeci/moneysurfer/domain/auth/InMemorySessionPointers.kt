package com.georgeci.moneysurfer.domain.auth

import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Volatile, RAM-only [SessionPointers] — does not persist across restarts. The production
 * Android/iOS/JVM app uses the DataStore-backed `SessionPointersImpl` from `:data`. This class
 * exists for unit tests and any non-persisting environment (REPL, scripts) that wants a
 * working session graph without dragging DataStore in.
 */
class InMemorySessionPointers(
    currentUserId: UserId? = null,
    currentWorkspaceId: WorkspaceId? = null,
    currentFirebaseUid: String? = null,
    hasUsedDemo: Boolean = false,
    currencyChosen: Boolean = true,
) : SessionPointers {
    override val currentUserId: Pref<UserId?> = Pref.inMemory(currentUserId)
    override val currentWorkspaceId: Pref<WorkspaceId?> = Pref.inMemory(currentWorkspaceId)
    override val currentFirebaseUid: Pref<String?> = Pref.inMemory(currentFirebaseUid)
    override val hasUsedDemo: Pref<Boolean> = Pref.inMemory(hasUsedDemo)
    override val currencyChosen: Pref<Boolean> = Pref.inMemory(currencyChosen)
}
