package com.georgeci.moneysurfer.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.data.datastore.core.PreferenceStore
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Session pointers on their own [PreferenceStore], no longer sharing `UiSettingsDataSource` with the
 * UI preferences — those moved onto the configuration engine, and these deliberately did not (see
 * ADR-004 §"Session pointers stay out": `null` means "nobody is signed in" here, which is
 * incompatible with the engine reading `null` from a layer as "absent").
 *
 * Key names and stored types are unchanged, so an existing install keeps its session.
 *
 * The empty string is the on-disk stand-in for "cleared" — DataStore preferences are non-null, and
 * removing the entry instead would mean re-deriving "cleared" from a missing key at every read.
 */
@Single(binds = [SessionPointers::class, SessionMutator::class])
class SessionPointersImpl(dataStore: DataStore<Preferences>) : SessionPointers, SessionMutator {

    private val prefs = PreferenceStore(dataStore)

    private val userId = prefs.custom(stringPreferencesKey("session.current_user_id"), CLEARED)
    private val workspaceId = prefs.custom(stringPreferencesKey("session.current_workspace_id"), CLEARED)

    /**
     * Firebase auth uid. Set when a Firebase-backed flow (login/signup/anon) succeeds, cleared on
     * logout. Stays empty for local-only sessions (demo), which lets callers detect "is there a
     * Firebase session right now?" without round-tripping through the auth SDK.
     */
    private val firebaseUid = prefs.custom(stringPreferencesKey("session.firebase_uid"), CLEARED)

    /**
     * Sticky flag set by `DemoLoginUseCase` and cleared by `WipeDemoDataUseCase`. Device-scoped: it
     * survives [clearSession] on purpose, because the auth flow consults it on the next real
     * login/signup to purge orphan demo data before bootstrapping. Demo data must never reach
     * Firestore — see sync.md §2.11.
     */
    private val hasUsedDemoPref = prefs.custom(booleanPreferencesKey("session.has_used_demo"), false)

    override val currentUserId: Flow<UserId?> = userId.flow.map { stored -> stored.orNull()?.let(::UserId) }

    override val currentWorkspaceId: Flow<WorkspaceId?> =
        workspaceId.flow.map { stored -> stored.orNull()?.let(::WorkspaceId) }

    override val currentFirebaseUid: Flow<String?> = firebaseUid.flow.map { stored -> stored.orNull() }

    override val hasUsedDemo: Flow<Boolean> = hasUsedDemoPref.flow

    override suspend fun setCurrentUser(id: UserId?) = userId.set(id?.value.orEmpty())

    override suspend fun setCurrentWorkspace(id: WorkspaceId?) = workspaceId.set(id?.value.orEmpty())

    override suspend fun setFirebaseUid(uid: String?) = firebaseUid.set(uid.orEmpty())

    override suspend fun setHasUsedDemo(value: Boolean) = hasUsedDemoPref.set(value)

    override suspend fun clearSession() {
        prefs.edit { preferences ->
            preferences[userId.key] = CLEARED
            preferences[workspaceId.key] = CLEARED
            preferences[firebaseUid.key] = CLEARED
        }
    }

    private fun String.orNull(): String? = ifEmpty { null }

    private companion object {
        const val CLEARED = ""
    }
}
