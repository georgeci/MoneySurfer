package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
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
 * Two-client happy-path convergence — owner pushes, peer pulls; peer pushes,
 * owner pulls. The conflict scenarios (concurrent edits, tombstones racing
 * local edits) live in [MultiClientConflictIT].
 *
 * Topology: two [AndroidIntegrationHarness] instances, each backed by a *named*
 * [com.google.firebase.FirebaseApp] (`owner-${tag}` / `peer-${tag}`). Same
 * Firebase emulator backend, but each side has its own Auth session,
 * Firestore client, and in-memory Room — exactly what two devices look like
 * to the server.
 *
 * Bootstrap (shared with [MultiClientConflictIT] via [bootstrapTwoClient])
 * leaves both sides synced to a baseline account. Each test method then
 * exercises a single propagation direction.
 *
 * Pre-req: Firebase Emulator Suite up — see [OutboxIdempotentRepushIT] for
 * the boot command.
 */
@RunWith(AndroidJUnit4::class)
class MultiClientConvergenceIT {

    private lateinit var ownerHarness: AndroidIntegrationHarness
    private lateinit var peerHarness: AndroidIntegrationHarness
    private val tag = System.currentTimeMillis().toString()

    @Before
    fun setUp() {
        ownerHarness = AndroidIntegrationHarness(appName = "owner-$tag")
        peerHarness = AndroidIntegrationHarness(appName = "peer-$tag")
    }

    @After
    fun tearDown() {
        peerHarness.close()
        ownerHarness.close()
    }

    @Test
    fun owner_edit_propagates_to_peer_via_outbox_drain_and_v2_pull() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "owner-version")

        // Sanity: peer received the baseline through the bootstrap pull.
        val peerBaseline = peerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("peer received owner's baseline") { peerBaseline.name shouldBe "owner-version" }

        // ── Owner edits → outbox drain ──────────────────────────────────────
        val editedUpdatedAt = ctx.seedUpdatedAt + 5_000L
        ownerHarness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = ctx.accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = "owner-edit",
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = editedUpdatedAt,
                ),
            ),
        )
        ownerHarness.outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ctx.accountId,
            scopeKey = ctx.workspaceId.value,
            operation = MutationOperation.UPDATE,
        )

        val ownerDrain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val ownerDrainOk = withClue("owner's outbox drain should succeed") {
            ownerDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        ownerDrainOk.value.uploadedCount shouldBe 1

        // ── Peer pulls — cursor delta picks up the edit only ────────────────
        val peerPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val peerPullOk = withClue("peer's pull should succeed") {
            peerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("peer's pull applied the owner edit: ${peerPullOk.value}") {
            peerPullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }

        val converged = peerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("peer converged to owner's edit") { converged.name shouldBe "owner-edit" }
        withClue("peer's row carries the edit's updatedAt") {
            converged.updatedAt shouldBe editedUpdatedAt
        }
        withClue("peer's account-cursor advanced to the edit") {
            peerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(editedUpdatedAt)
        }
    }

    @Test
    fun peer_edit_propagates_to_owner_via_outbox_drain_and_v2_pull() = runTest {
        // Mirror of the owner→peer test — same pipeline, opposite direction.
        // The pipeline is symmetric (rules treat any member's writes equivalently
        // for entity sub-collections), so a single direction proving the round
        // trip would not actually prove convergence on peer-originated writes.
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        // ── Peer edits → outbox drain (peer's auth, peer's FirebaseApp) ─────
        val peerEditUpdatedAt = ctx.seedUpdatedAt + 7_000L
        peerHarness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = ctx.accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = "peer-edit",
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = peerEditUpdatedAt,
                ),
            ),
        )
        peerHarness.outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ctx.accountId,
            scopeKey = ctx.workspaceId.value,
            operation = MutationOperation.UPDATE,
        )

        val peerDrain = peerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val peerDrainOk = withClue("peer's outbox drain should succeed") {
            peerDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        peerDrainOk.value.uploadedCount shouldBe 1

        // ── Owner pulls — cursor delta picks up peer's edit ─────────────────
        val ownerPull = ownerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val ownerPullOk = withClue("owner's pull should succeed") {
            ownerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("owner's pull applied peer's edit: ${ownerPullOk.value}") {
            ownerPullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }

        val ownerView = ownerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("owner converged to peer's edit") { ownerView.name shouldBe "peer-edit" }
        withClue("owner's row carries peer's updatedAt") {
            ownerView.updatedAt shouldBe peerEditUpdatedAt
        }
        withClue("owner's account-cursor advanced to peer's edit") {
            ownerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(peerEditUpdatedAt)
        }

        // Outbox stays clean on both sides — pull is read-only.
        withClue("owner's outbox stays empty (pull is read-only)") {
            ownerHarness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }
        withClue("peer's outbox empty after successful drain") {
            peerHarness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }
    }
}
