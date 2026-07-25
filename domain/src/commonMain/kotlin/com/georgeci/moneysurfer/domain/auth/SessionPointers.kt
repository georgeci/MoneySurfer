package com.georgeci.moneysurfer.domain.auth

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

/**
 * Session-scoped pointers persisted in DataStore. See sync.md §2.11 for the demo-flag semantics.
 *
 * Read-only on purpose: the pointers are written from exactly the session-lifecycle use cases
 * (see [SessionMutator]), while every other injection site — `Get*UseCase`, the invite use cases,
 * the pull path — only reads. Handing all of them a setter they must not call is how a stray write
 * ends up somewhere nobody thought to review.
 *
 * These are session state, not configuration, so they stay off the configuration engine: `null`
 * here means "nobody is signed in", while the engine reads `null` from a layer as "key absent",
 * which would turn clearing a pointer into falling through to the layer below.
 *
 * - [currentUserId] — local user id; set for every flow including demo. Drives "is anyone
 *   signed in" logic.
 * - [currentWorkspaceId] — active workspace; drives per-workspace queries (`Get*UseCase`).
 * - [currentFirebaseUid] — Firebase auth uid; non-null only for Firebase-backed sessions.
 *   Used by `CreateWorkspaceUseCase` to decide whether to push to Firestore.
 * - [hasUsedDemo] — sticky flag set by `DemoLoginUseCase`, consumed by `WipeDemoDataUseCase`
 *   on real login/signup so demo rows never leak into the real account's outbox.
 */
interface SessionPointers {
    val currentUserId: Flow<UserId?>
    val currentWorkspaceId: Flow<WorkspaceId?>
    val currentFirebaseUid: Flow<String?>
    val hasUsedDemo: Flow<Boolean>
}

/**
 * Write side of [SessionPointers] — injected only by session-lifecycle use cases (`Login`,
 * `Signup`, `AnonymousLogin`, `DemoLogin`, `Logout`, `DeleteUserAccount`, `SelectWorkspace`,
 * `CreateWorkspace`, `PostAuthBootstrap`, `AbandonAuthSession`, `AuthLocalRepository`,
 * `SyncCoordinatorWorkspaceSyncer`).
 */
interface SessionMutator {

    suspend fun setCurrentUser(id: UserId?)

    suspend fun setCurrentWorkspace(id: WorkspaceId?)

    suspend fun setFirebaseUid(uid: String?)

    suspend fun setHasUsedDemo(value: Boolean)

    /**
     * Clears user, workspace and Firebase uid in **one** store edit.
     *
     * Logout and account deletion used to write the three pointers as three separate DataStore
     * edits, so a crash in between left a half-cleared session (user id null, workspace id still
     * set). Must not touch [SessionPointers.hasUsedDemo] — that flag is device-scoped and clearing
     * it would let orphan demo data reach Firestore (sync.md §2.11).
     */
    suspend fun clearSession()
}
