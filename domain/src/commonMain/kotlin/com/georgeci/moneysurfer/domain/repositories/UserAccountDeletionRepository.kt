package com.georgeci.moneysurfer.domain.repositories

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError

/**
 * Remote-side cleanup for the user-account deletion flow (issue #213). Deletes everything the
 * hosted account-deletion page promises to remove — personal workspaces with their financial
 * records, the user's memberships, and the user/email lookup docs — while leaving content in
 * workspaces shared with other active members visible to those members.
 *
 * Implementation lives in `:data-remote` (online hosts only); the offline build binds a no-op.
 */
interface UserAccountDeletionRepository {

    /**
     * Idempotently deletes the remote footprint of [uid]:
     *  - workspaces owned by the user with no other ACTIVE member are hard-deleted
     *    (all subcollections, then the root doc),
     *  - in every other workspace the user's member row is marked LEFT (profile snapshots
     *    stay, matching the retention promise on the hosted deletion page),
     *  - the `userEmails/{email}` mapping (when it points at [uid]) and `users/{uid}` go last.
     *
     * Must be called while the user is still authenticated — Firestore rules authorize each
     * step against `request.auth.uid`. Deleting the Firebase Auth user is a separate step.
     */
    suspend fun deleteRemoteUserData(uid: String, email: String?): Either<AccountDeletionError, Unit>
}
