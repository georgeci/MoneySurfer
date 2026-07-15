package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncScope
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * Push-side soft delete: a local DELETE drains as an `update` that stamps
 * `deletedAt` / `updatedAt` / `clientVersionCode` instead of `firestore.delete()`
 * (rules deny hard deletes — `allow delete: if false`). Two scenarios:
 *
 *  - **Round trip**: owner deletes the baseline account → drain writes the
 *    tombstone (proving rules accept the update-with-deletedAt shape) → peer's
 *    cursor pull sees the tombstone and hard-deletes its local row. This is the
 *    propagation that plain `firestore.delete()` could never produce: the peer
 *    would keep the stale row forever.
 *  - **Never-pushed doc**: a DELETE whose doc never reached Firestore (entity
 *    created and deleted between drains) completes cleanly without creating a
 *    ghost doc and without wedging the queue on NOT_FOUND retries.
 *
 * Plus two rules-negative cases: `firestore.delete()` is PERMISSION_DENIED even
 * for a member (the invariant the design rests on), and a tombstone push into a
 * foreign workspace is denied with the row returned to PENDING.
 *
 * Pre-req: Firebase Emulator Suite up — see [OutboxIdempotentRepushIT] for the
 * boot command.
 */
@RunWith(AndroidJUnit4::class)
class TombstonePushIT {

    private lateinit var ownerHarness: AndroidIntegrationHarness
    private lateinit var peerHarness: AndroidIntegrationHarness
    private val tag = System.currentTimeMillis().toString()

    @Before
    fun setUp() {
        ownerHarness = AndroidIntegrationHarness(appName = "ts-owner-$tag")
        peerHarness = AndroidIntegrationHarness(appName = "ts-peer-$tag")
    }

    @After
    fun tearDown() {
        peerHarness.close()
        ownerHarness.close()
    }

    @Test
    fun owner_delete_pushes_tombstone_and_peer_pull_drops_the_row() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "doomed")

        val peerBaseline = peerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("peer received the baseline account through the bootstrap pull") {
            peerBaseline.name shouldBe "doomed"
        }

        // ── Owner deletes locally and enqueues the DELETE (production shape:
        //    repo reads the row for scopeKey, deletes, then enqueues). ─────────
        ownerHarness.database.accountDao().delete(ctx.accountId)
        ownerHarness.outboxEnqueuer.enqueueDelete(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ctx.accountId,
            scopeKey = ctx.workspaceId.value,
        )
        val deleteMutation = ownerHarness.pendingMutationQueue
            .pending(SyncScope.UploadOnly, limit = 10)
            .single()
        val expectedTombstoneMillis = deleteMutation.createdAt.toEpochMilliseconds()

        val drain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val drainOk = withClue("drain should succeed — rules must accept the tombstone update") {
            drain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        drainOk.value.uploadedCount shouldBe 1
        withClue("queue empty after successful drain") {
            ownerHarness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }

        // ── Remote doc survives as a tombstone, not a deletion. ──────────────
        val snap = ownerHarness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(ctx.accountId)
            .get()
        withClue("doc still exists remotely — soft delete, not firestore.delete()") {
            snap.exists shouldBe true
        }
        val remote = snap.data(AccountDoc.serializer())
        withClue("deletedAt stamped from the mutation's enqueue time") {
            remote.deletedAt shouldBe expectedTombstoneMillis
        }
        withClue("updatedAt bumped so cursor pulls fetch the tombstone") {
            remote.updatedAt shouldBe expectedTombstoneMillis
        }
        withClue("clientVersionCode re-stamped (rules floor is >= 1)") {
            remote.clientVersionCode shouldBeGreaterThanOrEqual 1
        }
        withClue("non-patched fields survive the field-mask update") {
            remote.name shouldBe "doomed"
        }

        // ── Peer pulls the tombstone via the cursor and drops its local row. ──
        val peerPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val peerPullOk = withClue("peer's pull should succeed") {
            peerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("peer's pull applied the tombstone: ${peerPullOk.value}") {
            peerPullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }
        withClue("peer's local row is gone — the stale-row-forever bug is closed") {
            peerHarness.database.accountDao().getById(ctx.accountId).shouldBeNull()
        }
        withClue("peer's account-cursor advanced past the tombstone") {
            peerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(expectedTombstoneMillis)
        }
    }

    @Test
    fun hard_delete_is_denied_by_rules_for_a_member() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "protected")

        val docRef = ownerHarness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(ctx.accountId)

        // The invariant the whole tombstone design rests on: rules reject
        // firestore.delete() even for a workspace member (`allow delete: if false`),
        // so soft-delete update is the only shape the server accepts.
        val denied = shouldThrow<FirebaseFirestoreException> { docRef.delete() }
        withClue("rules must deny hard delete, got: ${denied.message}") {
            denied.message.orEmpty().lowercase() shouldContain "permission"
        }
        withClue("doc untouched after the denied delete") {
            docRef.get().exists shouldBe true
        }
    }

    @Test
    fun tombstone_push_to_foreign_workspace_is_denied_and_row_returns_to_pending() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        // Workspace the signed-in owner is NOT a member of — rules' isMember(wid)
        // fails already on pushTombstone's existence read.
        val foreignWorkspaceId = "it-ts-foreign-$tag"
        ownerHarness.outboxEnqueuer.enqueueDelete(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ctx.accountId,
            scopeKey = foreignWorkspaceId,
        )

        val drain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("drain must surface the rule denial as Either.Left") {
            drain.shouldBeInstanceOf<Either.Left<*>>()
        }
        val row = ownerHarness.pendingMutationQueue.pending(SyncScope.UploadOnly, limit = 10).single()
        withClue("denied DELETE returns to PENDING with attempts bumped") {
            row.attempts shouldBe 1
        }
        row.lastError.shouldNotBeNull()
    }

    @Test
    fun delete_of_never_pushed_doc_drains_cleanly_without_creating_a_ghost() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        // Simulates create+delete between two drains: the INSERT push no-ops
        // once the Room row is gone, so the DELETE targets a doc that never
        // existed remotely.
        val phantomAccountId = "it-ts-phantom-$tag"
        ownerHarness.outboxEnqueuer.enqueueDelete(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = phantomAccountId,
            scopeKey = ctx.workspaceId.value,
        )

        val drain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val drainOk = withClue("drain must not fail on the missing doc") {
            drain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        drainOk.value.uploadedCount shouldBe 1
        withClue("queue empty — no NOT_FOUND retry loop") {
            ownerHarness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }

        val snap = ownerHarness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(phantomAccountId)
            .get()
        withClue("no ghost tombstone doc created for a doc peers never saw") {
            snap.exists shouldBe false
        }
    }
}
