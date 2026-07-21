package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCollection
import com.georgeci.moneysurfer.sync.api.SyncScope
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
 * Phase-2 pull pipeline ([com.georgeci.moneysurfer.data.sync.PullRemoteChangesUseCaseImpl]) end-to-end.
 *
 * Coverage:
 *  - Cursor-based incremental pull: first pull populates Room and advances the
 *    `(workspaceId, collection)` cursor; second pull reads `WHERE updatedAt > cursor`
 *    and returns `downloadedCount = 0`.
 *  - Outbox bypass: pulled rows go through DAOs, never through `OutboxEnqueuer` —
 *    `pending_mutations` stays empty after a pull.
 *  - LWW resolver against [com.georgeci.moneysurfer.sync.repository.LwwConflictResolver]:
 *    when the local row's `updatedAt` is newer than the incoming doc's, the
 *    resolution is `TakeLocal` (Room not overwritten, `conflictCount` bumped).
 *  - Tombstone propagation: a remote doc rewritten with `deletedAt != null` causes
 *    a hard local delete on the next pull; cursor still advances past it.
 *
 * Seeding strategy: production push via the v1 `WorkspaceSyncRepositoryImpl` lays
 * the docs down in Firestore (their toDoc mappers are `internal` to `:data`, so we
 * cannot construct DTOs from this module). The v2 pull is what we exercise.
 *
 * Pre-req: Firebase Emulator Suite up — see [OutboxIdempotentRepushIT] for the
 * boot command.
 */
@RunWith(AndroidJUnit4::class)
class PullCursorAndLwwIT {

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
    fun pull_populates_room_advances_cursor_skips_already_seen_and_bypasses_outbox() = runTest {
        val ctx = signInAndSeedWorkspace()

        // Wipe local entity tables (keep users + workspace_members + workspace doc) so
        // the pull writes to a clean slate. Workspace rows can stay — pull doesn't
        // touch the workspace doc (handled by v1).
        with(harness.database) {
            transactionDao().deleteAll()
            categoryDao().deleteAll()
            accountDao().deleteAll()
            // Drop the owner-member row too so we observe the member-pull leg:
            workspaceMemberDao().deleteAll()
        }

        // Pin the active workspace + Firebase uid so the use case has something to
        // resolve against.
        harness.session.currentWorkspaceId.set(ctx.workspaceId)
        harness.session.currentFirebaseUid.set(ctx.ownerUid)
        harness.session.currentUserId.set(UserId(ctx.ownerUid))

        // ── First pull ──────────────────────────────────────────────────────
        val firstPull = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val firstOk = withClue("first pull should succeed") {
            firstPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // Workspace root(1) + Members(1) + Invites(0) + Accounts(1) + Categories(1) +
        // Transactions(1) = 5 applied. The workspace root doc is applied on every pull
        // (see [WORKSPACE_ROOT_DOCS_PER_PULL]); the four subcollection rows are the
        // first-pull delta.
        withClue("first pull applied count: ${firstOk.value}") {
            firstOk.value.downloadedCount shouldBe WORKSPACE_ROOT_DOCS_PER_PULL + 4
        }
        withClue("no conflicts on first pull: ${firstOk.value}") {
            firstOk.value.conflictCount shouldBe 0
        }

        // Room repopulated by the pull (not by a separate push).
        with(harness.database) {
            withClue("account row pulled into Room") {
                accountDao().getById(ctx.accountId).shouldNotBeNull()
            }
            withClue("category row pulled into Room") {
                categoryDao().getById(ctx.categoryId).shouldNotBeNull()
            }
            withClue("transaction row pulled into Room") {
                transactionDao().getById(ctx.transactionId).shouldNotBeNull()
            }
            withClue("owner member row pulled into Room") {
                workspaceMemberDao().getById(
                    userId = ctx.ownerUid,
                    workspaceId = ctx.workspaceId.value,
                ).shouldNotBeNull()
            }
        }

        // Cursor for each non-empty collection advanced to ctx.seedUpdatedAt.
        // (Members and entities all share the same updatedAt in this seed, so the
        // cursor stops there for each.)
        val expectedCursor = Instant.fromEpochMilliseconds(ctx.seedUpdatedAt)
        for (collection in listOf(
            SyncCollection.WORKSPACE_MEMBERS,
            SyncCollection.ACCOUNTS,
            SyncCollection.CATEGORIES,
            SyncCollection.TRANSACTIONS,
        )) {
            withClue("$collection cursor advanced to seed updatedAt") {
                harness.syncMetaRepository.cursor(ctx.workspaceId.value, collection) shouldBe expectedCursor
            }
        }
        // Empty collection: markSuccess fires but cursor never set (no docs to read past).
        withClue("no invites pushed → cursor for invites stays null") {
            harness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.WORKSPACE_INVITES).shouldBeNull()
        }

        // Outbox stayed empty — pull goes through DAOs, not OutboxEnqueuer.
        withClue("pull must not enqueue mutations to the outbox") {
            harness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }

        // ── Second pull ─────────────────────────────────────────────────────
        // Every subcollection cursor sits at seedUpdatedAt; `WHERE updatedAt > cursor`
        // matches nothing. Only the always-applied workspace root doc is re-counted.
        val secondPull = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val secondOk = withClue("second pull should succeed") {
            secondPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("cursor blocks already-seen subcollection docs; only the workspace root re-counts: ${secondOk.value}") {
            secondOk.value.downloadedCount shouldBe WORKSPACE_ROOT_DOCS_PER_PULL
        }
        withClue("no conflicts on idle re-pull: ${secondOk.value}") {
            secondOk.value.conflictCount shouldBe 0
        }
    }

    @Test
    fun pull_takes_remote_when_remote_is_newer_and_overwrites_local_row() = runTest {
        val ctx = signInAndSeedWorkspace()
        harness.session.currentWorkspaceId.set(ctx.workspaceId)
        harness.session.currentFirebaseUid.set(ctx.ownerUid)
        harness.session.currentUserId.set(UserId(ctx.ownerUid))

        // Prime cursor at ctx.seedUpdatedAt so the next pull only sees the rewrite.
        val priming = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("priming pull should succeed") {
            priming.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }

        // Local row stays at ctx.seedUpdatedAt (set by signInAndSeedWorkspace + the
        // priming pull which roundtripped it back). Remote rewrite carries a strictly
        // newer updatedAt — LWW resolver returns TakeRemote.
        val remoteNewerUpdatedAt = ctx.seedUpdatedAt + 3_000L
        val remoteName = "remote-wins"

        rewriteAccountDoc(
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = remoteName,
            updatedAt = remoteNewerUpdatedAt,
            deletedAt = null,
        )

        val pull = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val pullOk = withClue("TakeRemote pull should succeed") {
            pull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        withClue("TakeRemote counts as applied: ${pullOk.value}") {
            pullOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }
        // TakeRemote is the no-conflict happy path — only TakeLocal/Merged/Skip bump
        // conflictCount (see PullRemoteChangesUseCaseImpl.applyResolution).
        withClue("TakeRemote does not count as conflict: ${pullOk.value}") {
            pullOk.value.conflictCount shouldBe 0
        }

        val afterPull = harness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("remote name overwrites local") { afterPull.name shouldBe remoteName }
        withClue("remote updatedAt overwrites local") { afterPull.updatedAt shouldBe remoteNewerUpdatedAt }

        withClue("cursor advances to remote updatedAt") {
            harness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(remoteNewerUpdatedAt)
        }

        withClue("pull must not enqueue mutations to the outbox") {
            harness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }
    }

    @Test
    fun pull_takes_local_when_local_is_newer_and_propagates_remote_tombstone() = runTest {
        val ctx = signInAndSeedWorkspace()
        harness.session.currentWorkspaceId.set(ctx.workspaceId)
        harness.session.currentFirebaseUid.set(ctx.ownerUid)
        harness.session.currentUserId.set(UserId(ctx.ownerUid))

        // Initial pull primes the cursor at ctx.seedUpdatedAt.
        val priming = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("priming pull should succeed") {
            priming.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }

        // ── LWW: bump local account to a newer updatedAt than the remote rewrite ─
        val localNewerUpdatedAt = ctx.seedUpdatedAt + 2_000L
        val remoteRewriteUpdatedAt = ctx.seedUpdatedAt + 1_000L // older than local
        val localName = "local-wins"
        val remoteName = "remote-loses"

        // Local-side modification — bypass the outbox by going through the DAO (just
        // like a sync-applier would, but here we're simulating "user edited locally
        // after the last pull").
        harness.database.accountDao().upsertAll(
            listOf(
                AccountEntity(
                    id = ctx.accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = localName,
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = localNewerUpdatedAt,
                ),
            ),
        )

        // Remote-side modification — a peer pushed a name change with an older
        // updatedAt than what we just wrote locally. This is the LWW conflict path
        // we want to exercise.
        rewriteAccountDoc(
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = remoteName,
            updatedAt = remoteRewriteUpdatedAt,
            deletedAt = null,
        )

        val lwwPull = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val lwwOk = withClue("LWW pull should succeed") {
            lwwPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // The remote doc was read (cursor < remoteRewriteUpdatedAt) but not applied —
        // local's newer updatedAt won.
        withClue("TakeLocal counts as a conflict: ${lwwOk.value}") {
            lwwOk.value.conflictCount shouldBeGreaterThanOrEqual 1
        }

        val afterLww = harness.database.accountDao().getById(ctx.accountId).shouldNotBeNull()
        withClue("local row preserved by TakeLocal") { afterLww.name shouldBe localName }
        withClue("local updatedAt untouched") { afterLww.updatedAt shouldBe localNewerUpdatedAt }

        // Cursor advances past the remote doc even though we didn't apply it — the
        // doc is "seen" once it enters the batch, regardless of resolution.
        withClue("cursor advances on TakeLocal too") {
            harness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(remoteRewriteUpdatedAt)
        }

        // ── Tombstone: remote rewrites the doc with deletedAt set, newer than ──
        // anything we've seen. Local row should be hard-deleted on next pull.
        val tombstoneUpdatedAt = ctx.seedUpdatedAt + 5_000L

        rewriteAccountDoc(
            workspaceId = ctx.workspaceId,
            accountId = ctx.accountId,
            name = "tombstoned",
            updatedAt = tombstoneUpdatedAt,
            deletedAt = tombstoneUpdatedAt,
        )

        val tombPull = harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val tombOk = withClue("tombstone pull should succeed") {
            tombPull.shouldBeInstanceOf<Either.Right<PullSummary>>()
        }
        // Tombstone applies regardless of LWW (it's a delete, not a content rewrite).
        withClue("tombstone counts as applied: ${tombOk.value}") {
            tombOk.value.downloadedCount shouldBeGreaterThanOrEqual 1
        }

        withClue("tombstone propagated → local row deleted") {
            harness.database.accountDao().getById(ctx.accountId).shouldBeNull()
        }
        withClue("cursor advances past the tombstone") {
            harness.syncMetaRepository.cursor(ctx.workspaceId.value, SyncCollection.ACCOUNTS) shouldBe
                Instant.fromEpochMilliseconds(tombstoneUpdatedAt)
        }

        // Outbox still pristine — neither the LWW conflict nor the tombstone leaked
        // into the queue.
        withClue("pull must not enqueue mutations to the outbox") {
            harness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }
    }

    /**
     * Sets up Auth + a workspace with one of each entity, pushed to Firestore via
     * v1 sync. Returns the identifiers + the `updatedAt` the seed used.
     *
     * Why `updatedAt = nowMillis` and not `0L`: the Phase-2 pull queries
     * `WHERE updatedAt > cursor` with `cursor ?: 0`. A doc with `updatedAt = 0`
     * is invisible to the very first pull.
     */
    private suspend fun signInAndSeedWorkspace(): Ctx {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-pull-owner-$tag@example.com"
        val ownerPassword = "owner-pwd-$tag"

        val ownerCreds = harness.env.auth.createUserWithEmailAndPassword(ownerEmail, ownerPassword)
        val ownerUid = ownerCreds.user!!.uid

        val workspaceId = WorkspaceId("it-pull-ws-$tag")
        val accountId = "it-pull-acc-$tag"
        val categoryId = "it-pull-cat-$tag"
        val transactionId = "it-pull-tx-$tag"
        val seedUpdatedAt = 1_700_000_000_000L

        with(harness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "Pull owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Pull IT $tag",
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
                    displayName = "Pull owner",
                    email = ownerEmail,
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
                        name = "Cash",
                        type = "CASH",
                        currency = "USD",
                        balance = 0L,
                        updatedAt = seedUpdatedAt,
                    ),
                ),
            )
            categoryDao().insertAll(
                listOf(
                    CategoryEntity(
                        id = categoryId,
                        workspaceId = workspaceId.value,
                        name = "Food",
                        type = "EXPENSE",
                        parentId = null,
                        createdAt = 0L,
                        updatedAt = seedUpdatedAt,
                    ),
                ),
            )
            transactionDao().insertAll(
                listOf(
                    TransactionEntity(
                        id = transactionId,
                        workspaceId = workspaceId.value,
                        accountId = accountId,
                        amount = -1000L,
                        currencyCode = "USD",
                        categoryId = categoryId,
                        note = "lunch",
                        operationAt = 1_700_000_000_000L,
                        type = "EXPENSE",
                        status = "ACTUAL",
                        createdAt = 1_700_000_000_000L,
                        updatedAt = seedUpdatedAt,
                    ),
                ),
            )
        }

        // Seed Firestore directly. Push now flows through the outbox; for this
        // fixture we write baseline docs so the cursor-based pull tests start from
        // a known Firestore state.
        val fs = harness.env.firestore
        fs.collection("workspaces").document(workspaceId.value).set(
            mapOf(
                "name" to "Pull IT $tag",
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
                    "displayName" to "Pull owner",
                    "email" to ownerEmail,
                    "addedByUserId" to ownerUid,
                    "createdAt" to 0L,
                    "updatedAt" to seedUpdatedAt,
                    "clientVersionCode" to 1,
                ),
            )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("accounts").document(accountId).set(
                mapOf(
                    "name" to "Cash",
                    "type" to "CASH",
                    "currency" to "USD",
                    "balance" to 0L,
                    "updatedAt" to seedUpdatedAt,
                    "clientVersionCode" to 1,
                ),
            )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("categories").document(categoryId).set(
                mapOf(
                    "name" to "Food",
                    "type" to "EXPENSE",
                    "parentId" to null,
                    "createdAt" to 0L,
                    "updatedAt" to seedUpdatedAt,
                    "clientVersionCode" to 1,
                ),
            )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("transactions").document(transactionId).set(
                mapOf(
                    "accountId" to accountId,
                    "amount" to -1000L,
                    "currencyCode" to "USD",
                    "categoryId" to categoryId,
                    "note" to "lunch",
                    "operationAt" to 1_700_000_000_000L,
                    "type" to "EXPENSE",
                    "status" to "ACTUAL",
                    "updatedAt" to seedUpdatedAt,
                    "clientVersionCode" to 1,
                ),
            )

        return Ctx(
            ownerUid = ownerUid,
            workspaceId = workspaceId,
            accountId = accountId,
            categoryId = categoryId,
            transactionId = transactionId,
            seedUpdatedAt = seedUpdatedAt,
        )
    }

    /**
     * Overwrites an account doc directly via Firestore using the same DTO the
     * production push path uses. Full-replace `set(...)`; `clientVersionCode`
     * defaults to 1 in [AccountDoc] which satisfies `firestore.rules`'
     * `hasValidClientVersion()`.
     */
    private suspend fun rewriteAccountDoc(
        workspaceId: WorkspaceId,
        accountId: String,
        name: String,
        updatedAt: Long,
        deletedAt: Long?,
    ) {
        harness.env.firestore
            .collection("workspaces")
            .document(workspaceId.value)
            .collection("accounts")
            .document(accountId)
            .set(
                AccountDoc(
                    name = name,
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = updatedAt,
                    deletedAt = deletedAt,
                ),
            )
    }

    private data class Ctx(
        val ownerUid: String,
        val workspaceId: WorkspaceId,
        val accountId: String,
        val categoryId: String,
        val transactionId: String,
        val seedUpdatedAt: Long,
    )
}
