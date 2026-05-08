package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.data.remote.CategoryDoc
import com.georgeci.moneysurfer.data.remote.TransactionDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Shared Room ↔ Firestore-DTO mappers. Used by both the v1
 * [com.georgeci.moneysurfer.data.repository.WorkspaceSyncRepositoryImpl] and the
 * Phase-1 outbox path (repo dual-writes serialize the DTO into the queue payload).
 *
 * `clientVersionCode` is set via the DTO defaults (currently `1`) — the static
 * Firestore rule `hasValidClientVersion()` requires `>= 1`. When real version
 * threading is wired (AppInfo.versionCode), bump the static default or move
 * to a per-call parameter.
 */

fun WorkspaceEntity.toDoc(): WorkspaceDoc = WorkspaceDoc(
    name = name,
    description = description,
    baseCurrency = baseCurrency,
    ownerId = ownerId,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkspaceDoc.toEntity(id: String): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    description = description,
    baseCurrency = baseCurrency,
    ownerId = ownerId,
    createdAt = createdAt,
    archived = archived,
    updatedAt = updatedAt,
)

fun AccountEntity.toDoc(): AccountDoc = AccountDoc(
    name = name,
    type = type,
    currency = currency,
    balance = balance,
    updatedAt = updatedAt,
)

fun AccountDoc.toEntity(id: String, workspaceId: String): AccountEntity = AccountEntity(
    id = id,
    workspaceId = workspaceId,
    name = name,
    type = type,
    currency = currency,
    balance = balance,
    updatedAt = updatedAt,
)

fun CategoryEntity.toDoc(): CategoryDoc = CategoryDoc(
    name = name,
    type = type,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    systemKind = systemKind,
)

fun CategoryDoc.toEntity(id: String, workspaceId: String): CategoryEntity = CategoryEntity(
    id = id,
    workspaceId = workspaceId,
    name = name,
    type = type,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    systemKind = systemKind,
)

fun TransactionEntity.toDoc(): TransactionDoc = TransactionDoc(
    accountId = accountId,
    categoryId = categoryId,
    amount = amount,
    currencyCode = currencyCode,
    note = note,
    operationAt = operationAt,
    operationDate = operationDate,
    type = type,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transferId = transferId,
)

fun TransactionDoc.toEntity(id: String, workspaceId: String): TransactionEntity {
    // Tolerate legacy/partial Firestore docs:
    //  - operationDate may be "" (older schema): derive ISO date from operationAt.
    //    Use UTC, not the device default, so the same Firestore doc resolves to the
    //    same operationDate on every client (avoids cross-device divergence).
    //  - createdAt may be 0L (older schema): fall back to operationAt or updatedAt
    //    so audit ordering doesn't collapse to epoch zero.
    val resolvedOperationDate = operationDate.ifBlank {
        Instant.fromEpochMilliseconds(operationAt)
            .toLocalDateTime(TimeZone.UTC)
            .date
            .toString()
    }
    val resolvedCreatedAt = if (createdAt == 0L) {
        if (operationAt != 0L) operationAt else updatedAt
    } else {
        createdAt
    }
    return TransactionEntity(
        id = id,
        workspaceId = workspaceId,
        accountId = accountId,
        amount = amount,
        currencyCode = currencyCode,
        categoryId = categoryId,
        note = note,
        operationAt = operationAt,
        operationDate = resolvedOperationDate,
        type = type,
        status = status,
        createdAt = resolvedCreatedAt,
        updatedAt = updatedAt,
        transferId = transferId,
    )
}

fun WorkspaceMemberEntity.toDoc(): WorkspaceMemberDoc = WorkspaceMemberDoc(
    userId = userId,
    role = role,
    status = status,
    displayName = displayName,
    email = email,
    addedByUserId = addedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    leftAt = leftAt,
    removedAt = removedAt,
)

fun WorkspaceMemberDoc.toEntity(workspaceId: String): WorkspaceMemberEntity = WorkspaceMemberEntity(
    userId = userId,
    workspaceId = workspaceId,
    role = role,
    status = status.ifEmpty { "ACTIVE" },
    displayName = displayName,
    email = email,
    addedByUserId = addedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    leftAt = leftAt,
    removedAt = removedAt,
)

fun WorkspaceInviteEntity.toDoc(): WorkspaceInviteDoc = WorkspaceInviteDoc(
    email = email,
    targetUserId = targetUserId,
    role = role,
    status = status,
    invitedByUserId = invitedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    respondedAt = respondedAt,
)

fun WorkspaceInviteDoc.toEntity(id: String, workspaceId: String): WorkspaceInviteEntity = WorkspaceInviteEntity(
    id = id,
    workspaceId = workspaceId,
    email = email,
    targetUserId = targetUserId,
    role = role,
    status = status.ifEmpty { "PENDING" },
    invitedByUserId = invitedByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    respondedAt = respondedAt,
)
