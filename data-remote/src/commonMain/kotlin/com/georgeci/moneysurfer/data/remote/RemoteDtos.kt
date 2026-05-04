package com.georgeci.moneysurfer.data.remote

import kotlinx.serialization.Serializable

/**
 * Firestore wire-format DTOs. Field names mirror Room entities 1:1 so a Firestore document
 * can round-trip through Kotlin serialization without a custom mapping layer.
 */

@Serializable
data class UserDoc(
    val displayName: String? = null,
    val email: String? = null,
    val isAnon: Boolean = false,
    val createdAt: Long = 0L,
    val workspaceIds: List<String> = emptyList(),
    val defaultWorkspaceId: String? = null,
    val invitedWorkspaceIds: List<String> = emptyList(),
)

/**
 * Email→uid mapping for invite recipient discovery. Doc id is the lowercased email.
 * Kept as a separate collection (rather than exposing `users.email` via list rules) so the
 * security rule is a tight `signedIn()` read on a single field with no other PII leakage.
 */
@Serializable
data class UserEmailDoc(
    val uid: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
data class WorkspaceDoc(
    val name: String = "",
    val description: String = "",
    val baseCurrency: String = "",
    val ownerId: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

@Serializable
data class AccountDoc(
    val name: String = "",
    val type: String = "",
    val currency: String = "",
    val balance: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

@Serializable
data class CategoryDoc(
    val name: String = "",
    val type: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

@Serializable
data class TransactionDoc(
    val accountId: String = "",
    val categoryId: String? = null,
    val amount: Long = 0L,
    val currencyCode: String = "",
    val note: String = "",
    val operationAt: Long = 0L,
    val operationDate: String = "",
    val type: String = "",
    val status: String = "ACTUAL",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Member doc lives at `workspaces/{wid}/members/{uid}`. Doc id = userId.
 *
 * Soft-delete via `status` (`ACTIVE` / `LEFT` / `REMOVED`) — `deletedAt` is kept for
 * back-compat with old docs and as a tombstone fallback for clients pulling members.
 */
@Serializable
data class WorkspaceMemberDoc(
    val userId: String = "",
    val role: String = "",
    val status: String = "ACTIVE",
    val displayName: String = "",
    val email: String? = null,
    val addedByUserId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val leftAt: Long? = null,
    val removedAt: Long? = null,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Invite doc lives at `workspaces/{wid}/invites/{inviteId}`. Doc id = invite UUID.
 */
@Serializable
data class WorkspaceInviteDoc(
    val email: String = "",
    val targetUserId: String? = null,
    val role: String = "",
    val status: String = "PENDING",
    val invitedByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val respondedAt: Long? = null,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)
