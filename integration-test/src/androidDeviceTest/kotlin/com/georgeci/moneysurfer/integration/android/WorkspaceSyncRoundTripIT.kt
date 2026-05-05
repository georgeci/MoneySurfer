package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Firestore → Room pull round-trip through the full plugin stack.
 *
 * Seeds workspace / member / account / category / transaction directly on the
 * Firestore emulator via the owner's authenticated client (so the `isMember(wid)`
 * read gate is exercised), then pulls via [PullRemoteChangesUseCaseImpl] and
 * asserts all rows landed in local Room.
 *
 * Replaces the old "push via WorkspaceSyncRepository, wipe local, pull back"
 * approach — push now flows through the outbox and is exercised separately in
 * [OutboxIdempotentRepushIT] and friends.
 *
 * Pre-req: Firebase Emulator Suite running locally:
 *
 *     firebase emulators:start --only firestore,auth --project demo-moneysurfer
 *
 * AVD reaches them at 10.0.2.2:8080 / 10.0.2.2:9099.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceSyncRoundTripIT {

    private lateinit var harness: AndroidIntegrationHarness

    @Before
    fun setUp() {
        harness = AndroidIntegrationHarness(appName = "roundtrip-it")
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun pulls_workspace_entities_into_room() = runTest {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-sync-owner-$tag@example.com"

        val ownerCreds = harness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "owner-pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val workspaceId = WorkspaceId("it-sync-ws-$tag")
        val accountId = "it-sync-acc-$tag"
        val categoryId = "it-sync-cat-$tag"
        val transactionId = "it-sync-tx-$tag"
        val now = 1_700_000_000_000L
        val fs = harness.env.firestore

        // ── Seed Firestore directly (owner auth exercises isMember read gate) ─
        fs.collection("workspaces").document(workspaceId.value).set(
            mapOf(
                "name" to "Sync IT $tag",
                "description" to "",
                "baseCurrency" to "USD",
                "ownerId" to ownerUid,
                "archived" to false,
                "createdAt" to now,
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
                    "displayName" to "Sync owner",
                    "email" to ownerEmail,
                    "addedByUserId" to ownerUid,
                    "createdAt" to now,
                    "updatedAt" to now,
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
                    "updatedAt" to now,
                    "clientVersionCode" to 1,
                ),
            )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("categories").document(categoryId).set(
                mapOf(
                    "name" to "Food",
                    "type" to "EXPENSE",
                    "parentId" to null,
                    "createdAt" to now,
                    "updatedAt" to now,
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
                    "updatedAt" to now,
                    "clientVersionCode" to 1,
                ),
            )

        // ── Seed Room prerequisites ────────────────────────────────────────────
        // The workspace row is not written by any sync plugin — it is bootstrapped
        // via the workspace-list pull or workspace creation flow. Pre-seed it so
        // account / category / transaction FK constraints don't fire during pull.
        // WorkspaceMemberSyncPlugin inserts unknown user rows via insertIgnore, so
        // only the ownerUid user row needs explicit pre-seeding.
        with(harness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "Sync owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Sync IT $tag",
                        description = "",
                        baseCurrency = "USD",
                        ownerId = ownerUid,
                        createdAt = now,
                        archived = false,
                        updatedAt = now,
                    ),
                ),
            )
        }

        // ── Pull ──────────────────────────────────────────────────────────────
        harness.session.currentFirebaseUid.set(ownerUid)
        harness.session.currentUserId.set(UserId(ownerUid))
        harness.session.currentWorkspaceId.set(workspaceId)

        harness.pullRemoteChanges(
            scope = SyncScope.ActiveWorkspace,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )

        // ── Verify all entities landed in Room ────────────────────────────────
        with(harness.database) {
            withClue("account should have been pulled") {
                accountDao().getByWorkspaceId(workspaceId.value).first().single().name shouldBe "Cash"
            }
            withClue("category should have been pulled") {
                categoryDao().getByWorkspaceId(workspaceId.value).first().single().name shouldBe "Food"
            }
            withClue("transaction amount should survive round-trip") {
                transactionDao().getByWorkspaceId(workspaceId.value).first().single().amount shouldBe -1000L
            }
            withClue("workspace should remain in Room") {
                workspaceDao().getAll().first().firstOrNull { it.id == workspaceId.value }.shouldNotBeNull()
            }
        }
    }
}
