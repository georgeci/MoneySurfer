package com.georgeci.moneysurfer.domain.auth

import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Session-scoped pointers persisted in DataStore. See CLAUDE.md §"Session pointers in DataStore"
 * and sync.md §2.11 for the demo-flag semantics.
 *
 * - [currentUserId] — local user id; set for every flow including demo. Drives "is anyone
 *   signed in" logic.
 * - [currentWorkspaceId] — active workspace; drives per-workspace queries (`Get*UseCase`).
 * - [currentFirebaseUid] — Firebase auth uid; non-null only for Firebase-backed sessions.
 *   Used by `CreateWorkspaceUseCase` to decide whether to push to Firestore.
 * - [hasUsedDemo] — sticky flag set by `DemoLoginUseCase`, consumed by `WipeDemoDataUseCase`
 *   on real login/signup so demo rows never leak into the real account's outbox.
 * - [currencyChosen] — sticky flag, `true` by default. The offline first-run seed flips it to
 *   `false` after auto-creating a workspace with a locale-derived currency, so `AppLaunchViewModel`
 *   routes to the currency picker before Dashboard. The picker flips it back to `true` on confirm,
 *   so a relaunch does not show the picker again.
 */
interface SessionPointers {
    val currentUserId: Pref<UserId?>
    val currentWorkspaceId: Pref<WorkspaceId?>
    val currentFirebaseUid: Pref<String?>
    val hasUsedDemo: Pref<Boolean>
    val currencyChosen: Pref<Boolean>
}
