package com.georgeci.moneysurfer.data.remote

import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus

/**
 * Remote primitives the account-deletion purge needs, expressed without any Firebase SDK type.
 * The Firestore-specific concerns each operation hides — document paths, wire DTOs, write-batch
 * chunking, exception classification — stay inside [FirebaseAccountDeletionRemoteSource]; the
 * purge/leave decision logic lives in
 * [UserAccountDeletionRepositoryImpl][com.georgeci.moneysurfer.data.repository.UserAccountDeletionRepositoryImpl]
 * and is unit-testable against a fake of this interface.
 *
 * Mirrors the seam already used for pulls
 * ([WorkspaceCollectionReader][com.georgeci.moneysurfer.data.sync.WorkspaceCollectionReader]).
 *
 * Reads return null / empty rather than throwing when a document is missing. When the caller
 * lacks access, implementations throw [RemoteAccessDeniedException] so the repository can skip a
 * stale workspace reference without aborting the whole deletion.
 */
// One flat list of purge primitives, deliberately over detekt's interface ceiling: every member is
// a step of the same flow, and splitting them into workspace-side and user-side interfaces would
// fragment a seam that has exactly one implementation and one caller.
@Suppress("TooManyFunctions")
interface AccountDeletionRemoteSource {

    /** Workspace ids referenced by `users/{uid}`. Empty when the user doc is gone. */
    suspend fun fetchWorkspaceIds(uid: String): List<String>

    /** `ownerId` of the workspace root doc, or null when it does not exist. */
    suspend fun fetchWorkspaceOwnerId(workspaceId: String): String?

    /** Every member row of the workspace. Missing statuses on legacy docs read as ACTIVE. */
    suspend fun fetchMembers(workspaceId: String): List<RemoteMemberRef>

    /** Hard-deletes every document in the named workspace subcollection. */
    suspend fun deleteWorkspaceCollection(workspaceId: String, collectionName: String)

    /** Hard-deletes the workspace root document. */
    suspend fun deleteWorkspace(workspaceId: String)

    /** Soft-leaves the workspace: member row → LEFT, stamping `leftAt` / `updatedAt`. */
    suspend fun markMemberLeft(workspaceId: String, uid: String, nowMillis: Long)

    /** uid stored in `userEmails/{emailKey}`, or null when there is no such mapping. */
    suspend fun fetchEmailMappingUid(emailKey: String): String?

    /** Deletes `userEmails/{emailKey}`. */
    suspend fun deleteEmailMapping(emailKey: String)

    /**
     * Hard-deletes every document in a subcollection of `users/{uid}`.
     *
     * Firestore does not cascade a document delete into its subcollections, so this has to run
     * *before* [deleteUserDoc]: once the Auth user is gone the documents are unreachable — no
     * client can authenticate as that uid again — and the personal data they hold would be orphaned
     * permanently.
     */
    suspend fun deleteUserCollection(uid: String, collectionName: String)

    /** Deletes `users/{uid}` — the final remote write of the deletion flow. */
    suspend fun deleteUserDoc(uid: String)
}

/** Member row reduced to what the purge decision needs. */
data class RemoteMemberRef(
    val uid: String,
    val status: WorkspaceMemberStatus,
)

/**
 * The backend refused the operation for the current user (Firestore PERMISSION_DENIED).
 * Raised by the remote source so the repository can react without importing an SDK type.
 */
class RemoteAccessDeniedException(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)
