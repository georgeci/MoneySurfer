package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Remote-backed user repository — reads/creates the `users/{uid}` document. Kept separate from
 * [UserRepository] (Room-backed local user table) because the lifecycles differ: the remote
 * record is the source of truth across devices, the local row is a cache + FK target.
 */
interface UserRemoteRepository {
    suspend fun fetch(uid: String): User?

    suspend fun create(
        uid: String,
        displayName: String?,
        email: String?,
        isAnon: Boolean,
        createdAt: Long,
    )

    /**
     * Appends [workspaceId] to `users/{uid}.workspaceIds`. Idempotent — uses Firestore's
     * `arrayUnion` so calling twice with the same id leaves the array unchanged.
     */
    suspend fun addWorkspaceRef(uid: String, workspaceId: WorkspaceId)

    /**
     * Pins [workspaceId] as `users/{uid}.defaultWorkspaceId` so other devices land on the
     * same workspace after login without re-prompting the selector.
     */
    suspend fun setDefaultWorkspace(uid: String, workspaceId: WorkspaceId)

    /**
     * Resolves [email] to a [UserId] via the `userEmails/{email.lower()}` mapping. Returns
     * null when no user has claimed that email (or when the mapping has not been backfilled
     * for a legacy account). Best-effort — callers must tolerate null and keep email-based
     * discovery as the fallback.
     */
    suspend fun findByEmail(email: String): UserId?

    /**
     * Idempotent self-write of the email→uid mapping at `userEmails/{email.lower()}`. Called
     * during post-auth bootstrap so future invites can resolve `targetUserId` for this user.
     */
    suspend fun upsertEmailMapping(email: String, uid: String)

    /**
     * Appends [workspaceId] to `users/{uid}.invitedWorkspaceIds`. Called when sending
     * an invite so the recipient's next syncAll() discovers the invite without a
     * collectionGroup query. Idempotent via Firestore arrayUnion.
     */
    suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId)

    /**
     * Removes [workspaceId] from `users/{uid}.invitedWorkspaceIds`. Called on accept/decline.
     * Idempotent via Firestore arrayRemove.
     */
    suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: WorkspaceId)
}
