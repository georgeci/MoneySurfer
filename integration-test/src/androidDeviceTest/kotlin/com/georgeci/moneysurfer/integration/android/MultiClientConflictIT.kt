package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.assertions.withClue
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
 * Cross-client conflict resolution scenarios. Complements:
 *  - [PullCursorAndLwwIT] — single-client LWW algorithm coverage (TakeRemote /
 *    TakeLocal / tombstone propagation) using manual Firestore rewrites to
 *    simulate "the other side."
 *  - [MultiClientConvergenceIT] — happy-path bidirectional propagation with no
 *    concurrent edits.
 *
 * What this class adds: two *real* Firestore clients (separate auth sessions,
 * separate FirebaseApps) racing edits + tombstones against each other through
 * the production outbox / pull pipeline. Verifies that the LWW invariants
 * established in unit tests hold end-to-end across the full
 * `Room → outbox → Firestore → cursor pull → Room` round trip.
 *
 * Each test starts from the [bootstrapTwoClient] baseline (both sides at
 * `seedUpdatedAt`) and diverges from there.
 *
 * Pre-req: Firebase Emulator Suite up — see [OutboxIdempotentRepushIT] for
 * the boot command.
 */
@RunWith(AndroidJUnit4::class)
class MultiClientConflictIT {

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

    /**
     * Concurrent edits race: owner writes at t1, peer writes the same row at
     * t2 > t1 without seeing owner's edit, both push. Firestore ends up with
     * peer's version (last-pusher-wins on the wire). Both clients pull and
     * converge to peer's version because:
     *  - On owner: local at t1, remote at t2 → TakeRemote (peer's t2 newer).
     *  - On peer: local at t2, remote at t2 → TakeLocal (equal — own echo).
     *
     * This is the canonical "two devices edit the same field while offline"
     * scenario the LWW resolver is designed to handle.
     */
    @Test
    fun concurrent_edits_lww_battle_later_writer_wins_on_both_sides() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        // ── Owner edits at t1 + drain ────────────────────────────────────────
        val ownerEditTs = ctx.seedUpdatedAt + 1_000L
        enqueueAccountEdit(
            harness = ownerHarness,
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = "owner-edit",
            updatedAt = ownerEditTs,
        )
        val ownerDrain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("owner drain succeeds") { ownerDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>() }

        // ── Peer edits at t2 > t1 + drain. Peer DOES NOT pull first — they
        //     edit on top of the stale local baseline, just like an offline
        //     device would. ────────────────────────────────────────────────────
        val peerEditTs = ctx.seedUpdatedAt + 5_000L
        enqueueAccountEdit(
            harness = peerHarness,
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = "peer-edit",
            updatedAt = peerEditTs,
        )
        val peerDrain = peerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("peer drain succeeds") { peerDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>() }

        // Wire-side state: peer's set() ran after owner's, so Firestore now has
        // peer's doc. Sanity-read it via the owner's client to confirm.
        val firestoreView = ownerHarness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(ctx.accountId)
            .get()
            .data(AccountDoc.serializer())
        withClue("Firestore reflects last-pusher-wins (peer)") {
            firestoreView.name shouldBe "peer-edit"
        }
        withClue("Firestore updatedAt = peer's t2") { firestoreView.updatedAt shouldBe peerEditTs }

        // ── Owner pulls — local at t1 vs remote at t2 → TakeRemote ──────────
        val ownerPull = ownerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val ownerPullOk = withClue("owner pull succeeds") {
            ownerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("owner pull applied peer's later write: ${ownerPullOk.value}") {
            ownerPullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }
        withClue("TakeRemote is the no-conflict happy path on the resolver: ${ownerPullOk.value}") {
            ownerPullOk.value.conflictCount shouldBe 0
        }
        val ownerLocalAfter = ownerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("owner converges to peer's later edit (LWW: t2 > t1)") {
            ownerLocalAfter.name shouldBe "peer-edit"
        }
        withClue("owner's local updatedAt = t2") { ownerLocalAfter.updatedAt shouldBe peerEditTs }

        // ── Peer pulls — fetches own echo. local at t2 == remote at t2 →
        //     TakeLocal (equal-ts tie-breaker). Cursor still advances. ────────
        val peerPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val peerPullOk = withClue("peer pull succeeds") {
            peerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // The account echo is TakeLocal (equal ts → local wins, not applied). Only the
        // always-applied workspace root doc is counted — see [WORKSPACE_ROOT_DOCS_PER_PULL].
        withClue("peer echo: account TakeLocal (not applied), only workspace root counts: ${peerPullOk.value}") {
            peerPullOk.value.downloadedCount shouldBe WORKSPACE_ROOT_DOCS_PER_PULL
        }
        withClue("TakeLocal counts as a (no-op) conflict: ${peerPullOk.value}") {
            peerPullOk.value.conflictCount shouldBeGreaterThanOrEqual 1
        }
        withClue("peer's local stays with its own value through the echo") {
            peerHarness.database.accountDao().getById(ctx.accountId)!!.name shouldBe "peer-edit"
        }

        // ── Both cursors land on peer's t2 — convergence proven. ────────────
        val expectedCursor = Instant.fromEpochMilliseconds(peerEditTs)
        withClue("owner cursor = t2") {
            ownerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe expectedCursor
        }
        withClue("peer cursor = t2") {
            peerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe expectedCursor
        }
    }

    /**
     * Tombstone races peer's pending local edit. Peer has an unsynced local
     * edit with `updatedAt = t2`; owner publishes a tombstone (deletedAt set)
     * with `updatedAt = t3 < t2`. Pull-side resolution of tombstones bypasses
     * LWW entirely (see [com.georgeci.moneysurfer.data.sync.PullRemoteChangesUseCaseImpl.applyAccount] —
     * `if (dto.deletedAt != null) accountDao.delete(...)`), so peer's local
     * row is hard-deleted regardless of which side has the newer `updatedAt`.
     *
     * Why this matters: a delete is structurally different from a content
     * update. If LWW applied, an offline edit could resurrect a deleted record
     * indefinitely. Tombstone-always-wins on the read path keeps the deletion
     * monotonic.
     */
    @Test
    fun tombstone_wins_over_peer_local_newer_unsynced_edit() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        // Peer makes a LOCAL edit (DAO only, no outbox push) at t2. Mirrors a
        // device that's been editing offline for a while.
        val peerLocalTs = ctx.seedUpdatedAt + 5_000L
        peerHarness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = ctx.accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = "peer-unsynced",
                    type = "CASH",
                    currency = "USD",
                    balance = 999L,
                    updatedAt = peerLocalTs,
                ),
            ),
        )

        // Owner publishes a tombstone with t3 < peerLocalTs (deletedAt set).
        // Push it through Firestore directly using the owner's authenticated
        // client — production would go via outbox + uploadPendingChanges, but
        // we want a single deterministic write here.
        val tombstoneTs = ctx.seedUpdatedAt + 2_000L
        ownerHarness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(ctx.accountId)
            .set(
                AccountDoc(
                    name = "tombstoned",
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = tombstoneTs,
                    deletedAt = tombstoneTs,
                ),
            )

        // Peer pulls. Cursor at seedUpdatedAt; tombstoneTs > seedUpdatedAt →
        // doc is fetched. deletedAt-branch in applyAccount fires regardless of
        // peer's local being newer.
        val peerPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val peerPullOk = withClue("peer pull succeeds") {
            peerPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("tombstone counts as applied: ${peerPullOk.value}") {
            peerPullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }

        withClue("tombstone hard-deleted peer's row even though peer's local was newer") {
            peerHarness.database.accountDao().getById(ctx.accountId).shouldBeNull()
        }
        withClue("cursor advances past the tombstone") {
            peerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(tombstoneTs)
        }

        // Outbox stays empty — the tombstone arrived via pull, not via a local
        // mutation. (Peer's earlier upsert was DAO-only, no outbox.)
        withClue("pull doesn't enqueue mutations") {
            peerHarness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }
    }

    /**
     * Equal-`updatedAt` tie-breaker. Both clients independently write the same
     * row at the same `updatedAt` (different content). The LWW resolver's docs
     * call out this case: «Equal timestamps → local wins». Verifies the rule
     * is stable end-to-end so a re-pull doesn't oscillate.
     */
    @Test
    fun equal_updatedAt_resolves_to_take_local_and_is_stable_across_repulls() = runTest {
        val ctx = bootstrapTwoClient(ownerHarness, peerHarness, tag, initialAccountName = "baseline")

        val sharedTs = ctx.seedUpdatedAt + 3_000L

        // Owner pushes content "owner-eq" at sharedTs.
        enqueueAccountEdit(
            harness = ownerHarness,
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = "owner-eq",
            updatedAt = sharedTs,
        )
        val ownerDrain = ownerHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("owner drain") { ownerDrain.shouldBeInstanceOf<Either.Right<UploadSummary>>() }

        // Peer makes a local edit (DAO only) at the SAME sharedTs but with
        // different content. No outbox push — we want the local-versus-remote
        // race at the resolver, not a wire-level overwrite.
        peerHarness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = ctx.accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = "peer-eq",
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = sharedTs,
                ),
            ),
        )

        // First pull on peer — local at sharedTs == remote at sharedTs → TakeLocal.
        val firstPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val firstOk = withClue("peer first pull") {
            firstPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // Account is TakeLocal (equal ts → not applied). Only the always-applied
        // workspace root doc is counted — see [WORKSPACE_ROOT_DOCS_PER_PULL].
        withClue("TakeLocal does not apply the account; only workspace root counts: ${firstOk.value}") {
            firstOk.value.downloadedCount shouldBe WORKSPACE_ROOT_DOCS_PER_PULL
        }
        withClue("TakeLocal counts as conflict: ${firstOk.value}") {
            firstOk.value.conflictCount shouldBeGreaterThanOrEqual 1
        }
        val afterFirst = peerHarness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("peer's local content survives equal-ts tie") { afterFirst.name shouldBe "peer-eq" }
        withClue("cursor advances past the equal-ts doc") {
            peerHarness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(sharedTs)
        }

        // Second pull — cursor is at sharedTs, query > sharedTs returns nothing.
        // No oscillation.
        val secondPull = peerHarness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val secondOk = withClue("peer second pull") {
            secondPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // Account cursor is at sharedTs → no account delta on re-pull. The workspace root
        // doc is still re-applied and counted (see [WORKSPACE_ROOT_DOCS_PER_PULL]).
        withClue("stable: no subcollection delta, only workspace root re-counts: ${secondOk.value}") {
            secondOk.value.downloadedCount shouldBe WORKSPACE_ROOT_DOCS_PER_PULL
        }
        withClue("stable: no second-round conflict") { secondOk.value.conflictCount shouldBe 0 }
        withClue("peer content unchanged across re-pulls") {
            peerHarness.database.accountDao().getById(ctx.accountId)!!.name shouldBe "peer-eq"
        }
    }

    private suspend fun enqueueAccountEdit(
        harness: AndroidIntegrationHarness,
        workspaceId: WorkspaceId,
        accountId: String,
        name: String,
        updatedAt: Long,
    ) {
        harness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = accountId,
                    workspaceId = workspaceId.value,
                    name = name,
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = updatedAt,
                ),
            ),
        )
        harness.outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = accountId,
            scopeKey = workspaceId.value,
            operation = MutationOperation.UPDATE,
        )
    }
}
