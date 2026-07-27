package com.georgeci.moneysurfer.data.repository

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.remote.AccountDeletionRemoteSource
import com.georgeci.moneysurfer.data.remote.RemoteAccessDeniedException
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.logging.redactUid
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository
import com.georgeci.moneysurfer.sync.api.SyncCollection
import org.koin.core.annotation.Single

/**
 * Remote cleanup for account deletion (issue #213). Every step is idempotent — a retry after a
 * part-way failure re-runs against whatever documents are left. The caller
 * ([DeleteUserAccountUseCase][com.georgeci.moneysurfer.domain.usecase.DeleteUserAccountUseCase])
 * guarantees sync is shut down first, so nothing re-creates docs behind the purge.
 *
 * Deletion semantics mirror the hosted account-deletion page: workspaces that are effectively
 * personal (owned, no other ACTIVE member) are hard-deleted; in workspaces shared with other
 * people the user's member row flips to LEFT and the content stays for the remaining members.
 *
 * All Firestore specifics sit behind [AccountDeletionRemoteSource], so this class holds only the
 * decision logic and is unit-tested against a fake.
 */
@Single(binds = [UserAccountDeletionRepository::class])
class UserAccountDeletionRepositoryImpl(
    private val remote: AccountDeletionRemoteSource,
    private val clock: ClockUseCase,
) : UserAccountDeletionRepository {

    private val log = Logger.withTag(TAG)

    override suspend fun deleteRemoteUserData(
        uid: String,
        email: String?,
    ): Either<AccountDeletionError, Unit> = Either.catch {
        val workspaceIds = remote.fetchWorkspaceIds(uid)
        log.i { "[delete] start uid=${uid.redactUid()} workspaces=${workspaceIds.size}" }

        workspaceIds.forEach { workspaceId ->
            try {
                disposeWorkspace(workspaceId, uid)
            } catch (e: RemoteAccessDeniedException) {
                // A stale ref in users/{uid}.workspaceIds (left / evicted earlier) is denied —
                // there is nothing of the user's left to clean up there.
                log.w(e) { "[delete] no access to workspace=$workspaceId — skipping stale ref" }
            }
        }

        deleteEmailMapping(email, uid)
        // Before the user document, never after: Firestore does not cascade deletes into
        // subcollections, and once `users/{uid}` and the Auth user are gone nobody can ever
        // authenticate as this uid again to reach what is left behind.
        USER_COLLECTIONS.forEach { name -> remote.deleteUserCollection(uid, name) }
        remote.deleteUserDoc(uid)
        log.i { "[delete] remote data cleared uid=${uid.redactUid()}" }
    }.mapLeft { AccountDeletionError.RemoteDataCleanupFailed(cause = it) }

    private suspend fun disposeWorkspace(workspaceId: String, uid: String) {
        val ownerId = remote.fetchWorkspaceOwnerId(workspaceId) ?: return
        val members = remote.fetchMembers(workspaceId)
        val hasOtherActiveMembers = members.any {
            it.uid != uid && it.status == WorkspaceMemberStatus.ACTIVE
        }

        if (ownerId == uid && !hasOtherActiveMembers) {
            log.i { "[delete] purging personal workspace=$workspaceId" }
            purgeWorkspace(workspaceId)
        } else {
            log.i { "[delete] leaving shared workspace=$workspaceId" }
            leaveWorkspace(workspaceId, uid, members.firstOrNull { it.uid == uid }?.status)
        }
    }

    /**
     * Hard-delete of a personal workspace. Members go last among subcollections (listing the
     * others requires an ACTIVE membership row) and the root doc goes after everything — the
     * subcollection delete rules read it via `isOwner()`, so it must outlive them.
     */
    private suspend fun purgeWorkspace(workspaceId: String) {
        ENTITY_COLLECTIONS.forEach { name -> remote.deleteWorkspaceCollection(workspaceId, name) }
        remote.deleteWorkspaceCollection(workspaceId, "members")
        remote.deleteWorkspace(workspaceId)
    }

    /**
     * Marks the user's own member row LEFT instead of deleting it — profile snapshots stay
     * visible to remaining members, matching the retention promise on the hosted page. A row the
     * owner already flipped to REMOVED is left alone (rules deny self-updates on it), as is a
     * workspace the user has no row in.
     */
    private suspend fun leaveWorkspace(workspaceId: String, uid: String, status: WorkspaceMemberStatus?) {
        if (status != WorkspaceMemberStatus.ACTIVE) return
        remote.markMemberLeft(workspaceId, uid, clock.now().toEpochMilliseconds())
    }

    /** Removes the email→uid lookup row, but only when it still points at the deleted user. */
    private suspend fun deleteEmailMapping(email: String?, uid: String) {
        val key = email?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return
        if (remote.fetchEmailMappingUid(key) == uid) {
            remote.deleteEmailMapping(key)
        }
    }

    private companion object {
        const val TAG = "AccountDeletion"

        /**
         * Subcollections of `users/{uid}`, purged before the user document itself. Named through
         * [SyncCollection] rather than spelled out: the pull reads the same path, and a collection
         * missing from this list is orphaned silently — the documents outlive the Auth user with
         * nobody able to authenticate as it again.
         */
        val USER_COLLECTIONS = listOf(SyncCollection.USER_CONFIG)

        /** Workspace subcollections purged before members + root doc. */
        val ENTITY_COLLECTIONS = listOf(
            "accounts",
            "categories",
            "transactions",
            "invites",
            "budgets",
            "recurringRules",
            "goalContributions",
            "goals",
        )
    }
}
