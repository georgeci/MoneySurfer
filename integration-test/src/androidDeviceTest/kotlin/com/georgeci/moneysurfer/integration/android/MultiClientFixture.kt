package com.georgeci.moneysurfer.integration.android

import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope

/**
 * Shared bootstrap for any two-client integration test that needs both sides
 * pre-synced to a baseline before exercising conflict / convergence behavior.
 *
 * Post-condition (symmetric on both sides):
 *  - Auth: each harness has its own signed-in user (`ownerUid`, `peerUid`).
 *  - Firestore: workspace doc + 2 member docs (owner + peer) + 1 account doc,
 *    all stamped with `clientVersionCode = 1` (DTO default), `updatedAt =
 *    seedUpdatedAt`.
 *  - Owner's Room: full seed (users + workspace + 2 members + 1 account).
 *  - Peer's Room: workspace shell + 2 users + 2 member rows + 1 account row
 *    (the account row arrives via the v2 pull below, not the manual seed).
 *  - Both sides' `(workspaceId, ACCOUNTS)` cursor is at `seedUpdatedAt`, so any
 *    subsequent pull only sees deltas at `updatedAt > seedUpdatedAt`.
 *  - Outbox empty on both sides (direct Firestore seed + v2 pull bypass it).
 *
 * Why no category / transaction in the seed: keeps the FK graph small. The
 * `transactions.accountId → accounts.id` FK has no `onDelete = CASCADE` (see
 * `TransactionEntity`), so any later tombstone of the account would otherwise
 * trigger SQLite 787. Tests that need richer seeds extend this fixture.
 */
internal data class MultiClientCtx(
    val ownerUid: String,
    val peerUid: String,
    val ownerEmail: String,
    val peerEmail: String,
    val workspaceId: WorkspaceId,
    val accountId: String,
    val seedUpdatedAt: Long,
)

internal suspend fun bootstrapTwoClient(
    ownerHarness: AndroidIntegrationHarness,
    peerHarness: AndroidIntegrationHarness,
    tag: String,
    initialAccountName: String = "baseline",
): MultiClientCtx {
    // ── Sign in each side on its own FirebaseApp/Auth ─────────────────────────
    val ownerEmail = "it-mc-owner-$tag@example.com"
    val peerEmail = "it-mc-peer-$tag@example.com"

    val ownerCreds = ownerHarness.env.auth.createUserWithEmailAndPassword(
        ownerEmail,
        "owner-pwd-$tag",
    )
    val ownerUid = ownerCreds.user!!.uid
    val peerCreds = peerHarness.env.auth.createUserWithEmailAndPassword(
        peerEmail,
        "peer-pwd-$tag",
    )
    val peerUid = peerCreds.user!!.uid

    val workspaceId = WorkspaceId("it-mc-ws-$tag")
    val accountId = "it-mc-acc-$tag"
    val seedUpdatedAt = 1_700_000_000_000L

    // ── Owner-side Room seed ──────────────────────────────────────────────────
    with(ownerHarness.database) {
        userDao().upsert(UserEntity(id = ownerUid, displayName = "MC Owner", isAnon = false))
        userDao().upsert(UserEntity(id = peerUid, displayName = "MC Peer", isAnon = false))
        workspaceDao().insertAll(
            listOf(
                WorkspaceEntity(
                    id = workspaceId.value,
                    name = "MC IT $tag",
                    description = "",
                    baseCurrency = "USD",
                    ownerId = ownerUid,
                    createdAt = 0L,
                    archived = false,
                    updatedAt = seedUpdatedAt,
                ),
            ),
        )
        workspaceMemberDao().upsert(
            WorkspaceMemberEntity(
                userId = ownerUid,
                workspaceId = workspaceId.value,
                role = "OWNER",
                status = "ACTIVE",
                displayName = "MC Owner",
                email = ownerEmail,
                addedByUserId = ownerUid,
                createdAt = 0L,
                updatedAt = seedUpdatedAt,
            ),
        )
        workspaceMemberDao().upsert(
            WorkspaceMemberEntity(
                userId = peerUid,
                workspaceId = workspaceId.value,
                role = "EDITOR",
                status = "ACTIVE",
                displayName = "MC Peer",
                email = peerEmail,
                addedByUserId = ownerUid,
                createdAt = 0L,
                updatedAt = seedUpdatedAt,
            ),
        )
        accountDao().insertAll(
            listOf(
                AccountEntity(
                    id = accountId,
                    workspaceId = workspaceId.value,
                    name = initialAccountName,
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = seedUpdatedAt,
                ),
            ),
        )
    }

    ownerHarness.session.setFirebaseUid(ownerUid)
    ownerHarness.session.setCurrentUser(UserId(ownerUid))
    ownerHarness.session.setCurrentWorkspace(workspaceId)

    // ── Seed Firestore directly (owner auth) ──────────────────────────────────
    // Push now flows through the outbox; for the bootstrap fixture we write the
    // baseline state directly so the peer pull finds all docs on its first sync.
    val fs = ownerHarness.env.firestore
    fs.collection("workspaces").document(workspaceId.value).set(
        mapOf(
            "name" to "MC IT $tag",
            "description" to "",
            "baseCurrency" to "USD",
            "ownerId" to ownerUid,
            "archived" to false,
            "createdAt" to 0L,
            "updatedAt" to seedUpdatedAt,
            "clientVersionCode" to 1,
        ),
    )
    fs.collection("workspaces").document(workspaceId.value)
        .collection("members").document(ownerUid).set(
            mapOf(
                "userId" to ownerUid,
                "role" to "OWNER",
                "status" to "ACTIVE",
                "displayName" to "MC Owner",
                "email" to ownerEmail,
                "addedByUserId" to ownerUid,
                "createdAt" to 0L,
                "updatedAt" to seedUpdatedAt,
                "clientVersionCode" to 1,
            ),
        )
    fs.collection("workspaces").document(workspaceId.value)
        .collection("members").document(peerUid).set(
            mapOf(
                "userId" to peerUid,
                "role" to "EDITOR",
                "status" to "ACTIVE",
                "displayName" to "MC Peer",
                "email" to peerEmail,
                "addedByUserId" to ownerUid,
                "createdAt" to 0L,
                "updatedAt" to seedUpdatedAt,
                "clientVersionCode" to 1,
            ),
        )
    fs.collection("workspaces").document(workspaceId.value)
        .collection("accounts").document(accountId).set(
            mapOf(
                "name" to initialAccountName,
                "type" to "CASH",
                "currency" to "USD",
                "balance" to 0L,
                "updatedAt" to seedUpdatedAt,
                "clientVersionCode" to 1,
            ),
        )

    // ── Peer-side: pre-seed workspace shell, pin session, v2 pull ─────────────
    // workspaces.ownerId has FK → users.id; accounts.workspaceId has FK →
    // workspaces.id. Stub both before the pull writes the member/account rows
    // (the pull's applyMember does insertIgnore for peer userIds, but doesn't
    // create the workspace row).
    with(peerHarness.database) {
        userDao().upsert(UserEntity(id = peerUid, displayName = "MC Peer", isAnon = false))
        userDao().upsert(UserEntity(id = ownerUid, displayName = "MC Owner", isAnon = false))
        workspaceDao().insertAll(
            listOf(
                WorkspaceEntity(
                    id = workspaceId.value,
                    name = "MC IT $tag",
                    description = "",
                    baseCurrency = "USD",
                    ownerId = ownerUid,
                    createdAt = 0L,
                    archived = false,
                    updatedAt = seedUpdatedAt,
                ),
            ),
        )
    }

    peerHarness.session.setFirebaseUid(peerUid)
    peerHarness.session.setCurrentUser(UserId(peerUid))
    peerHarness.session.setCurrentWorkspace(workspaceId)

    peerHarness.pullRemoteChanges(
        scope = SyncScope.ActiveWorkspace,
        onProgress = {},
        cancelToken = SimpleCancelToken(),
    )

    // ── Owner's pull primes their cursor too. Without this the owner's first
    //     post-bootstrap pull would re-fetch all the equal-`updatedAt` baseline
    //     docs (cursor null → 0 → all match), turning every test into "first
    //     pull is noisy". With the prime, the next owner pull is a clean delta. ─
    ownerHarness.pullRemoteChanges(
        scope = SyncScope.ActiveWorkspace,
        onProgress = {},
        cancelToken = SimpleCancelToken(),
    )

    return MultiClientCtx(
        ownerUid = ownerUid,
        peerUid = peerUid,
        ownerEmail = ownerEmail,
        peerEmail = peerEmail,
        workspaceId = workspaceId,
        accountId = accountId,
        seedUpdatedAt = seedUpdatedAt,
    )
}
