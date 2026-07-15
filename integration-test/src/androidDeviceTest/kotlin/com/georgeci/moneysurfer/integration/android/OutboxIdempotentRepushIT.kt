package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end coverage of the bug we shipped a rule fix for: when an invite
 * UPDATE mutation lands on Firestore but the client misses the ack, the
 * outbox retries the same UPDATE on an already-ACCEPTED doc. Before the rule
 * change the retry got `PERMISSION_DENIED` forever; now the rule allows
 * idempotent re-push of the same terminal status.
 *
 * Wires the production stack (in-memory Room + gitlive Firestore client →
 * emulator) and exercises:
 *  1. Recipient marks the invite ACCEPTED locally → outbox enqueues UPDATE.
 *  2. First drain pushes successfully.
 *  3. We mark accepted AGAIN to force a fresh outbox mutation that mirrors a
 *     missed-ack retry on an already-terminal server doc.
 *  4. Second drain succeeds — proves the rule's idempotent branch is live.
 *
 * Requires the Firebase Emulator Suite running locally on the host:
 *
 *     firebase emulators:start \
 *       --only firestore,auth \
 *       --project demo-moneysurfer
 *
 * AVD reaches them at `10.0.2.2:8080` / `10.0.2.2:9099`.
 */
@RunWith(AndroidJUnit4::class)
class OutboxIdempotentRepushIT {

    private lateinit var harness: AndroidIntegrationHarness

    @Before
    fun setUp() {
        harness = AndroidIntegrationHarness()
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun outbox_drains_when_retrying_already_accepted_invite() = runTest {
        // ── Identifiers (uniqued so re-runs against the same emulator don't collide) ─
        val tag = System.currentTimeMillis().toString()
        val ownerUid = "it-owner-$tag"
        val inviteeEmail = "it-invitee-$tag@example.com"
        val inviteePassword = "password-$tag"
        val workspaceId = WorkspaceId("it-ws-$tag")
        val inviteId = WorkspaceInviteId("it-inv-$tag")

        // ── Sign in as recipient via the Auth emulator ──────────────────────
        // Email match in the rule needs `request.auth.token.email`; uid match
        // needs `request.auth.uid`. The emulator issues both for newly created
        // users, so the rule's targetUserId branch fires regardless of which
        // we test against.
        val recipient = harness.env.auth.createUserWithEmailAndPassword(
            email = inviteeEmail,
            password = inviteePassword,
        )
        val recipientUid = recipient.user!!.uid

        // ── Seed Firestore: workspace doc + PENDING invite (admin-style write) ─
        // We keep auth signed-in as the recipient to mirror the real-life flow:
        // the inviter's own outbox push (signed in as owner) created these docs
        // earlier — for THIS test we cut to the recipient's view of the world.
        // Rules let `create` happen because we're seeding via raw Firestore set
        // and the recipient's targetUserId/email match the doc — meaning the
        // create rule's `isOwner(wid)` clause fails but the `set()` updates an
        // existing doc once we've upserted via admin path. Easier: just write
        // both docs while temporarily signed-out — emulator default rules
        // don't apply to signed-out writes... actually they do. So we sign in
        // as a fresh "owner" account briefly to seed.
        val ownerEmail = "it-owner-$tag@example.com"
        val ownerCreds = harness.env.auth.createUserWithEmailAndPassword(
            email = ownerEmail,
            password = "owner-$tag",
        )
        val ownerRealUid = ownerCreds.user!!.uid

        val workspacesCol = harness.env.firestore.collection("workspaces")
        workspacesCol.document(workspaceId.value).set(
            mapOf(
                "name" to "IT workspace $tag",
                "ownerId" to ownerRealUid,
                "createdAt" to 0L,
                "updatedAt" to 0L,
                "clientVersionCode" to 1,
            ),
        )
        // Owner must be a member so subsequent `isMember(wid)` calls (e.g. for
        // the invite roster) succeed — also satisfies firestore.rules's create
        // rule for the invite below (`isOwner(wid)` requires the workspace doc
        // we just wrote and a member row isn't strictly required, but seeding
        // it keeps us aligned with prod data shape).
        workspacesCol.document(workspaceId.value)
            .collection("members")
            .document(ownerRealUid)
            .set(
                mapOf(
                    "role" to "OWNER",
                    "status" to "ACTIVE",
                    "displayName" to "Owner $tag",
                    "email" to ownerEmail,
                    "createdAt" to 0L,
                    "updatedAt" to 0L,
                    "clientVersionCode" to 1,
                ),
            )
        workspacesCol.document(workspaceId.value)
            .collection("invites")
            .document(inviteId.value)
            .set(
                mapOf(
                    "email" to inviteeEmail,
                    "targetUserId" to recipientUid,
                    "role" to WorkspaceRole.EDITOR.name,
                    "status" to InviteStatus.PENDING.name,
                    "invitedByUserId" to ownerRealUid,
                    "createdAt" to 0L,
                    "updatedAt" to 0L,
                    "expiresAt" to Long.MAX_VALUE,
                    "clientVersionCode" to 1,
                ),
            )

        // Switch to the recipient session and pin local sessionPointers so
        // OutboxEnqueuer's signed-in-aware bits behave the same as in prod.
        harness.env.auth.signOut()
        harness.env.auth.signInWithEmailAndPassword(inviteeEmail, inviteePassword)
        harness.session.currentUserId.set(com.georgeci.moneysurfer.domain.primitives.UserId(recipientUid))
        harness.session.currentFirebaseUid.set(recipientUid)

        // Mirror the doc into the recipient's local Room VIA THE DAO — bypassing
        // the outbox to match production: `IncomingInviteRemoteRepositoryImpl`
        // upserts pulled invites through `WorkspaceInviteDao.upsert(...)` directly
        // so the pulled doc doesn't echo back as a push. Going through the
        // repository (`inviteRepository.upsert`) would enqueue a redundant INSERT
        // and the test would push PENDING-as-PENDING, which the rule denies.
        harness.database.workspaceInviteDao().upsert(
            WorkspaceInviteEntity(
                id = inviteId.value,
                workspaceId = workspaceId.value,
                email = inviteeEmail,
                targetUserId = recipientUid,
                role = WorkspaceRole.EDITOR.name,
                status = InviteStatus.PENDING.name,
                invitedByUserId = ownerRealUid,
                createdAt = 0L,
                updatedAt = 0L,
                expiresAt = Long.MAX_VALUE,
                respondedAt = null,
            ),
        )

        // ── Act 1: local accept → outbox enqueue → first drain ───────────────
        harness.inviteRepository.markAccepted(inviteId)
        val firstDrain = harness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val firstOk = withClue("first drain should succeed") {
            firstDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        withClue("first drain should upload 1 mutation") { firstOk.value.uploadedCount shouldBe 1 }

        // ── Act 2: same transition again → fresh stale-style mutation ────────
        // Mirrors the "client missed the ack" failure mode: outbox still has a
        // mutation, server already applied a matching transition, retry must
        // be idempotent. `markAccepted` is idempotent at the local level — it
        // re-enqueues an UPDATE for an already-ACCEPTED row. That's exactly
        // the shape of a recovered-after-crash retry.
        harness.inviteRepository.markAccepted(inviteId)
        val secondDrain = harness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val secondOk = withClue("second drain (idempotent re-push) should succeed") {
            secondDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        withClue("second drain should re-push the 1 idempotent mutation") {
            secondOk.value.uploadedCount shouldBe 1
        }

        // Local Room reflects the terminal state (markAccepted wrote it both
        // times). The drain succeeding (Either.Right above) is the authoritative
        // signal that the server-side rule allowed the idempotent re-push —
        // a Left would carry PERMISSION_DENIED here. Skipping the explicit
        // server-side read keeps this test free of `:data`-internal serializers.
        harness.database.workspaceInviteDao().getById(inviteId.value)!!.status shouldBe
            InviteStatus.ACCEPTED.name
    }
}
