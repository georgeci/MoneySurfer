package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldContainKey
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Phase-1 outbox drain coverage that complements [OutboxIdempotentRepushIT]
 * (which proves the rule's idempotent re-push branch on terminal-status invites).
 *
 * Two test cases:
 *  - **Happy path**: seed `AccountEntity` in Room → `enqueueUpsert` → `uploadPendingChanges`
 *    → Firestore has the doc and the queue is empty. The plugin reads the entity
 *    from Room at push time (no payload in outbox since sync.md §4.3 migration).
 *  - **Partial-batch failure**: a batch of `[ok_1, rule_denied, ok_2]` where
 *    `rule_denied` targets a workspace the signed-in user is NOT a member of
 *    (so `isMember(wid)` evaluates false → `PERMISSION_DENIED`). Verifies:
 *    (a) `ok_1`, which was pushed successfully before the failure, is removed
 *    from the queue; (b) `rule_denied` AND `ok_2` (which the drain never
 *    reached after the throw) are reset to `PENDING` with `attempts=1` and a
 *    non-null `lastError`.
 *
 * Pre-req: Firebase Emulator Suite up — see [OutboxIdempotentRepushIT] for the
 * boot command.
 */
@RunWith(AndroidJUnit4::class)
class OutboxDrainAndRecoveryIT {

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
    fun enqueue_then_drain_pushes_doc_to_firestore_and_empties_queue() = runTest {
        val ctx = signInAndBootstrapWorkspace()

        val accountId = "it-drain-acc-${ctx.tag}"

        // Seed Room — AccountSyncPlugin.push reads the entity at push time.
        harness.database.accountDao().insertAll(
            listOf(
                AccountEntity(
                    id = accountId,
                    workspaceId = ctx.workspaceId.value,
                    name = "Drain Cash",
                    type = "CASH",
                    currency = "USD",
                    balance = 0L,
                    updatedAt = ctx.now,
                ),
            ),
        )

        // Production enqueue path: OutboxEnqueuer.isEnabled() requires
        // `currentFirebaseUid` non-empty AND `appVersionGate.isSyncAllowed()` —
        // signInAndBootstrapWorkspace + the harness's always-supported gate
        // satisfy both.
        harness.outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = accountId,
            scopeKey = ctx.workspaceId.value,
            operation = MutationOperation.INSERT,
        )
        withClue("enqueueUpsert made the row visible in the queue") {
            harness.pendingMutationQueue.pendingCount.first() shouldBe 1
        }

        val drain = harness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        val drainOk = withClue("drain should succeed") {
            drain.shouldBeInstanceOf<Either.Right<UploadSummary>>()
        }
        drainOk.value.uploadedCount shouldBe 1
        withClue("queue empty after successful drain (markCompleted deleted the row)") {
            harness.pendingMutationQueue.pendingCount.first() shouldBe 0
        }

        // Server-side read-back — proves the doc actually landed AND the rules'
        // `clientVersionCode >= 1` floor was satisfied (plugin re-stamps it from appInfo).
        val snap = harness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(accountId)
            .get()
        withClue("remote doc exists after drain") { snap.exists shouldBe true }
        val remote = snap.data(AccountDoc.serializer())
        remote.name shouldBe "Drain Cash"
        withClue("drain re-stamps clientVersionCode (>=1 is the rule floor)") {
            remote.clientVersionCode shouldBeGreaterThanOrEqual 1
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun partial_batch_failure_marks_remaining_pending_with_attempts_bumped() = runTest {
        val ctx = signInAndBootstrapWorkspace()

        // Workspace that the signed-in user is NOT a member of. Writing to its
        // accounts sub-collection trips Firestore rules' `isMember(wid)` check
        // → PERMISSION_DENIED → use case raises.
        val foreignWorkspaceId = WorkspaceId("it-drain-foreign-${ctx.tag}")

        val ok1AccountId = "it-drain-acc-ok1-${ctx.tag}"
        val ok2AccountId = "it-drain-acc-ok2-${ctx.tag}"
        val deniedAccountId = "it-drain-acc-denied-${ctx.tag}"

        // Seed Room: plugin reads from Room at push time. The foreign workspace
        // entity is pre-seeded so the accountDao FK constraint doesn't fire.
        with(harness.database) {
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = foreignWorkspaceId.value,
                        name = "Foreign WS",
                        description = "",
                        baseCurrency = "USD",
                        ownerId = ctx.ownerUid,
                        createdAt = 0L,
                        archived = false,
                        updatedAt = ctx.now,
                    ),
                ),
            )
            accountDao().insertAll(
                listOf(
                    AccountEntity(
                        id = ok1AccountId,
                        workspaceId = ctx.workspaceId.value,
                        name = "ok-1",
                        type = "CASH",
                        currency = "USD",
                        balance = 0L,
                        updatedAt = ctx.now,
                    ),
                    AccountEntity(
                        id = deniedAccountId,
                        workspaceId = foreignWorkspaceId.value,
                        name = "denied",
                        type = "CASH",
                        currency = "USD",
                        balance = 0L,
                        updatedAt = ctx.now,
                    ),
                    AccountEntity(
                        id = ok2AccountId,
                        workspaceId = ctx.workspaceId.value,
                        name = "ok-2",
                        type = "CASH",
                        currency = "USD",
                        balance = 0L,
                        updatedAt = ctx.now,
                    ),
                ),
            )
        }

        // Direct enqueue (bypassing OutboxEnqueuer) so we can pin createdAt and
        // guarantee drain order [ok_1, denied, ok_2]. OutboxEnqueuerImpl uses
        // Clock.System.now() which has only ms resolution — three calls in the
        // same coroutine would tie and the DAO's ORDER BY createdAt ASC is
        // unstable on ties.
        val baseCreatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L)
        val okMutation1 = PendingMutation(
            id = Uuid.random().toString(),
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ok1AccountId,
            operation = MutationOperation.INSERT,
            scopeKey = ctx.workspaceId.value,
            createdAt = baseCreatedAt,
            attempts = 0,
            lastError = null,
        )
        val deniedMutation = PendingMutation(
            id = Uuid.random().toString(),
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = deniedAccountId,
            operation = MutationOperation.INSERT,
            scopeKey = foreignWorkspaceId.value,
            createdAt = Instant.fromEpochMilliseconds(baseCreatedAt.toEpochMilliseconds() + 1L),
            attempts = 0,
            lastError = null,
        )
        val okMutation2 = PendingMutation(
            id = Uuid.random().toString(),
            entityType = SyncEntityTypes.ACCOUNT,
            entityId = ok2AccountId,
            operation = MutationOperation.INSERT,
            scopeKey = ctx.workspaceId.value,
            createdAt = Instant.fromEpochMilliseconds(baseCreatedAt.toEpochMilliseconds() + 2L),
            attempts = 0,
            lastError = null,
        )

        harness.pendingMutationQueue.enqueue(okMutation1)
        harness.pendingMutationQueue.enqueue(deniedMutation)
        harness.pendingMutationQueue.enqueue(okMutation2)
        harness.pendingMutationQueue.pendingCount.first() shouldBe 3

        // ── Drain: should fail (PERMISSION_DENIED on the foreign workspace). ──
        val drain = harness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("drain must surface the rule denial as Either.Left") {
            drain.shouldBeInstanceOf<Either.Left<*>>()
        }

        // ── ok_1 was pushed before the failure → markCompleted (deleted). ─────
        // denied + ok_2 were never completed → markFailed (status=PENDING,
        // attempts++, lastError stamped). The use case never reaches ok_2's
        // pushOne because the catch block runs as soon as denied throws.
        val pendingAfter = harness.pendingMutationQueue.pending(SyncScope.UploadOnly, limit = 100)
        pendingAfter shouldHaveSize 2
        val byId = pendingAfter.associateBy { it.id }
        withClue("denied mutation still in queue (rule-denied)") {
            byId shouldContainKey deniedMutation.id
        }
        withClue("ok_2 still in queue (drain abandoned the rest of the batch)") {
            byId shouldContainKey okMutation2.id
        }

        for (row in pendingAfter) {
            withClue("markFailed bumps attempts from 0 → 1 for ${row.entityId}") {
                row.attempts shouldBe 1
            }
            withClue("markFailed stamps lastError for ${row.entityId}") {
                row.lastError.shouldNotBeNull()
            }
        }

        // ── ok_1 reached Firestore — proves the per-doc completed-before-throw
        // path actually committed the upstream side too. ──────────────────────
        val ok1Snap = harness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(okMutation1.entityId)
            .get()
        withClue("ok_1 doc should exist remotely (it succeeded before denied threw)") {
            ok1Snap.exists shouldBe true
        }

        // ok_2 was never pushed — drain abandoned the rest of the batch.
        val ok2Snap = harness.env.firestore
            .collection("workspaces")
            .document(ctx.workspaceId.value)
            .collection("accounts")
            .document(okMutation2.entityId)
            .get()
        withClue("ok_2 doc should NOT exist remotely — drain bailed before reaching it") {
            ok2Snap.exists shouldBe false
        }
    }

    /**
     * Sets up Auth + Room + Firestore so the user is signed in and a member of a
     * fresh workspace. Returns the identifiers + a base updatedAt for seeding.
     *
     * Mirrors [PullCursorAndLwwIT.signInAndSeedWorkspace] but lighter — no
     * accounts/categories/transactions seeded; the outbox tests build those
     * themselves.
     */
    private suspend fun signInAndBootstrapWorkspace(): Bootstrap {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-drain-owner-$tag@example.com"

        val creds = harness.env.auth.createUserWithEmailAndPassword(ownerEmail, "owner-pwd-$tag")
        val ownerUid = creds.user!!.uid

        val workspaceId = WorkspaceId("it-drain-ws-$tag")
        val now = 1_700_000_000_000L

        with(harness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "Drain owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Drain IT $tag",
                        description = "",
                        baseCurrency = "USD",
                        ownerId = ownerUid,
                        createdAt = 0L,
                        archived = false,
                        updatedAt = now,
                    ),
                ),
            )
            workspaceMemberDao().upsert(
                WorkspaceMemberEntity(
                    userId = ownerUid,
                    workspaceId = workspaceId.value,
                    role = "OWNER",
                    status = "ACTIVE",
                    displayName = "Drain owner",
                    email = ownerEmail,
                    addedByUserId = ownerUid,
                    createdAt = 0L,
                    updatedAt = now,
                ),
            )
        }

        // Seed workspace + member directly so `isMember(wid)` returns true for
        // our uid. Push now flows through the outbox; the bootstrap only needs
        // the two docs that gate subsequent sub-collection writes.
        val fs = harness.env.firestore
        fs.collection("workspaces").document(workspaceId.value).set(
            mapOf(
                "name" to "Drain IT $tag",
                "description" to "",
                "baseCurrency" to "USD",
                "ownerId" to ownerUid,
                "archived" to false,
                "createdAt" to 0L,
                "updatedAt" to now,
                "clientVersionCode" to 1,
            ),
        )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid).set(
                mapOf(
                    "userId" to ownerUid,
                    "role" to "OWNER",
                    "status" to "ACTIVE",
                    "displayName" to "Drain owner",
                    "email" to ownerEmail,
                    "addedByUserId" to ownerUid,
                    "addedAt" to 0L,
                    "updatedAt" to now,
                    "clientVersionCode" to 1,
                ),
            )

        harness.session.currentFirebaseUid.set(ownerUid)
        harness.session.currentUserId.set(UserId(ownerUid))
        harness.session.currentWorkspaceId.set(workspaceId)

        return Bootstrap(
            tag = tag,
            ownerUid = ownerUid,
            workspaceId = workspaceId,
            now = now,
        )
    }

    private data class Bootstrap(
        val tag: String,
        val ownerUid: String,
        val workspaceId: WorkspaceId,
        val now: Long,
    )
}
