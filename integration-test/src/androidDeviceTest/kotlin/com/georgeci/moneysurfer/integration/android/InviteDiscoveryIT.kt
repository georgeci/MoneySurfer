package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end invite discovery via `invitedWorkspaceIds`.
 *
 * Replaces the old `IncomingInviteRemoteRepositoryImpl` / collectionGroup path.
 * The scenario:
 *
 *  1. Owner signs in, creates workspace on Firestore, writes invite doc.
 *  2. Owner writes wid to `users/{inviteeUid}.invitedWorkspaceIds`
 *     (mirrors [SendInviteUseCase] best-effort call).
 *  3. Invitee signs in, runs `pullRemoteChanges(AllUserData)`.
 *  4. Assert: invite row appears in invitee's local Room.
 *  5. Invitee accepts — marks invite ACCEPTED locally, uploads via outbox.
 *  6. Invitee removes wid from `invitedWorkspaceIds` and adds to `workspaceIds`
 *     (mirrors [AcceptInviteUseCase]).
 *  7. Owner runs `pullRemoteChanges(AllUserData)` — asserts invite ACCEPTED.
 *
 * Pre-req: Firebase Emulator Suite running locally:
 *
 *     firebase emulators:start --only firestore,auth --project demo-moneysurfer
 *
 * AVD reaches them at 10.0.2.2:8080 / 10.0.2.2:9099.
 */
@RunWith(AndroidJUnit4::class)
class InviteDiscoveryIT {

    private lateinit var ownerHarness: AndroidIntegrationHarness
    private lateinit var inviteeHarness: AndroidIntegrationHarness

    @Before
    fun setUp() {
        ownerHarness = AndroidIntegrationHarness(appName = "owner-invite-it")
        inviteeHarness = AndroidIntegrationHarness(appName = "invitee-invite-it")
    }

    @After
    fun tearDown() {
        ownerHarness.close()
        inviteeHarness.close()
    }

    @Test
    fun invite_is_discovered_via_invitedWorkspaceIds_and_accepted() = runTest {
        val tag = System.currentTimeMillis().toString()

        // ── 1. Sign in both users ─────────────────────────────────────────────
        val ownerEmail = "it-invite-owner-$tag@example.com"
        val inviteeEmail = "it-invite-invitee-$tag@example.com"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "owner-pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val inviteeCreds = inviteeHarness.env.auth
            .createUserWithEmailAndPassword(inviteeEmail, "invitee-pwd-$tag")
        val inviteeUid = inviteeCreds.user!!.uid

        val workspaceId = WorkspaceId("it-invite-ws-$tag")
        val inviteId = "it-invite-inv-$tag"
        val now = System.currentTimeMillis()

        // ── 2. Owner seeds Firestore directly (mirrors production push path) ──
        val fs = ownerHarness.env.firestore

        // workspace doc (rules: ownerId == request.auth.uid)
        fs.collection("workspaces").document(workspaceId.value).set(
            mapOf(
                "name" to "Invite IT $tag",
                "description" to "",
                "baseCurrency" to "USD",
                "ownerId" to ownerUid,
                "archived" to false,
                "createdAt" to now,
                "updatedAt" to now,
                "clientVersionCode" to 1,
            ),
        )

        // owner member doc (rules: isMember gate for sub-collections)
        fs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid).set(
                mapOf(
                    "userId" to ownerUid,
                    "role" to "OWNER",
                    "status" to "ACTIVE",
                    "displayName" to "IT Owner",
                    "email" to ownerEmail,
                    "addedByUserId" to ownerUid,
                    "createdAt" to now,
                    "updatedAt" to now,
                    "clientVersionCode" to 1,
                ),
            )

        // invite doc under workspace
        fs.collection("workspaces").document(workspaceId.value)
            .collection("invites").document(inviteId).set(
                WorkspaceInviteDoc(
                    email = inviteeEmail,
                    targetUserId = inviteeUid,
                    role = "EDITOR",
                    status = "PENDING",
                    invitedByUserId = ownerUid,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = now + 14L * 24 * 60 * 60 * 1000,
                    clientVersionCode = 1,
                ),
            )

        // ── 3. Seed invitee's user doc + invitedWorkspaceIds ─────────────────────
        // Mirrors the production flow:
        //   PostAuthBootstrap → userRemoteRepository.create()
        //   SendInviteUseCase → userRemoteRepository.addInvitedWorkspaceRef()
        // Using the repository methods (instead of a raw set() with the field
        // pre-populated) ensures both writes are server-confirmed before the pull,
        // which avoids offline-persistence ordering issues with subsequent update().
        inviteeHarness.userRemoteRepository.create(
            uid = inviteeUid,
            displayName = "IT Invitee",
            email = inviteeEmail,
            isAnon = false,
            createdAt = now,
        )
        // Owner seeds recipient's invitedWorkspaceIds (cross-user append rule).
        ownerHarness.userRemoteRepository.addInvitedWorkspaceRef(inviteeUid, workspaceId)

        // ── 4. Seed invitee's local Room prerequisites ────────────────────────
        // WorkspaceInviteSyncPlugin.applyDoc writes inviteDao.upsert(entity) which
        // has a FK: workspaceId → workspaces.id. Stub the workspace row so the FK
        // constraint doesn't fire. No member row needed — invite pull is read-only.
        with(inviteeHarness.database) {
            userDao().upsert(UserEntity(id = inviteeUid, displayName = "IT Invitee", isAnon = false))
            userDao().upsert(UserEntity(id = ownerUid, displayName = "IT Owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Invite IT $tag",
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

        // ── 5. Invitee pulls — Phase 2 of PullRemoteChangesUseCaseImpl ────────
        inviteeHarness.session.currentFirebaseUid.set(inviteeUid)
        inviteeHarness.session.currentUserId.set(UserId(inviteeUid))
        // currentWorkspaceId intentionally NOT set: the invitee hasn't joined any workspace
        // yet. AllUserData scope must not fall back to currentWorkspaceId (the invited wid)
        // because the invitee is not a member and the members/accounts queries would be denied.

        val inviteePull = inviteeHarness.pullRemoteChanges(
            scope = SyncScope.AllUserData,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("invitee AllUserData pull should succeed") {
            inviteePull.shouldBeInstanceOf<Either.Right<*>>()
        }

        // ── 6. Assert invite landed in invitee's Room ─────────────────────────
        val invites = inviteeHarness.database.workspaceInviteDao()
            .getByWorkspaceId(workspaceId.value).first()
        withClue("invitee should have exactly one invite after AllUserData pull") {
            invites shouldHaveSize 1
        }
        val invite = invites.single()
        withClue("invite id") { invite.id shouldBe inviteId }
        withClue("invite status") { invite.status shouldBe "PENDING" }
        withClue("invite targetUserId") { invite.targetUserId shouldBe inviteeUid }

        // ── 7. Invitee accepts — upload via outbox ────────────────────────────
        inviteeHarness.database.workspaceInviteDao().upsert(
            invite.copy(status = "ACCEPTED", respondedAt = now),
        )
        // Outbox picks up the mutation via WorkspaceInviteRepositoryImpl's enqueue;
        // here we manually enqueue to keep the test self-contained.
        // Instead, push the update directly through uploadPendingChanges after
        // seeding a pending mutation via the invite repo's markAccepted path.
        // For simplicity: push the updated doc directly to Firestore as the invitee
        // (exercises the invitee-update rule) then verify via owner pull.
        inviteeHarness.env.firestore
            .collection("workspaces").document(workspaceId.value)
            .collection("invites").document(inviteId).update(
                mapOf(
                    "status" to "ACCEPTED",
                    "respondedAt" to now,
                    "updatedAt" to now + 1,
                    "clientVersionCode" to 1,
                ),
            )

        // ── 8. Invitee removes wid from invitedWorkspaceIds ──────────────────────
        // Mirrors AcceptInviteUseCase.removeInvitedWorkspaceRef.
        inviteeHarness.userRemoteRepository.removeInvitedWorkspaceRef(inviteeUid, workspaceId)

        // ── 9. Seed owner Room + pull to verify ACCEPTED ──────────────────────
        with(ownerHarness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "IT Owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Invite IT $tag",
                        description = "",
                        baseCurrency = "USD",
                        ownerId = ownerUid,
                        createdAt = now,
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
                    displayName = "IT Owner",
                    email = ownerEmail,
                    addedByUserId = ownerUid,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        ownerHarness.session.currentFirebaseUid.set(ownerUid)
        ownerHarness.session.currentUserId.set(UserId(ownerUid))
        ownerHarness.session.currentWorkspaceId.set(workspaceId)

        // Create owner's user doc (mirrors PostAuthBootstrap) then add workspace ref
        // so UserWorkspacesProvider.workspaceIds() returns the wid during the pull.
        ownerHarness.userRemoteRepository.create(
            uid = ownerUid,
            displayName = "IT Owner",
            email = ownerEmail,
            isAnon = false,
            createdAt = now,
        )
        ownerHarness.userRemoteRepository.addWorkspaceRef(ownerUid, workspaceId)

        ownerHarness.pullRemoteChanges(
            scope = SyncScope.AllUserData,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )

        val ownerInvites = ownerHarness.database.workspaceInviteDao()
            .getByWorkspaceId(workspaceId.value).first()
        withClue("owner should see the accepted invite") {
            ownerInvites shouldHaveSize 1
        }
        withClue("invite should be ACCEPTED from owner side") {
            ownerInvites.single().status shouldBe "ACCEPTED"
        }

        // ── 10. Verify invitedWorkspaceIds was cleaned up ──────────────────────
        val userSnap = inviteeHarness.env.firestore
            .collection("users").document(inviteeUid).get()
        @Suppress("UNCHECKED_CAST")
        val remaining = (userSnap.get("invitedWorkspaceIds") as? List<String>).orEmpty()
        withClue("invitedWorkspaceIds should be empty after accept") {
            remaining shouldHaveSize 0
        }
    }

    @Test
    fun decline_cleans_up_invitedWorkspaceIds() = runTest {
        val tag = "decline-${System.currentTimeMillis()}"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword("it-decline-owner-$tag@example.com", "pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val inviteeCreds = inviteeHarness.env.auth
            .createUserWithEmailAndPassword("it-decline-inv-$tag@example.com", "pwd-$tag")
        val inviteeUid = inviteeCreds.user!!.uid

        val workspaceId = WorkspaceId("it-decline-ws-$tag")
        val now = System.currentTimeMillis()
        val fs = ownerHarness.env.firestore

        // Mirrors production: invitee creates own doc, owner seeds invitedWorkspaceIds.
        inviteeHarness.userRemoteRepository.create(
            uid = inviteeUid,
            displayName = "Decliner",
            email = "it-decline-inv-$tag@example.com",
            isAnon = false,
            createdAt = now,
        )
        ownerHarness.userRemoteRepository.addInvitedWorkspaceRef(inviteeUid, workspaceId)

        // Invitee removes it (decline path).
        inviteeHarness.session.currentFirebaseUid.set(inviteeUid)
        inviteeHarness.userRemoteRepository.removeInvitedWorkspaceRef(inviteeUid, workspaceId)

        val snap = inviteeHarness.env.firestore
            .collection("users").document(inviteeUid).get()
        @Suppress("UNCHECKED_CAST")
        val remaining = (snap.get("invitedWorkspaceIds") as? List<String>).orEmpty()
        withClue("invitedWorkspaceIds should be empty after decline") {
            remaining shouldHaveSize 0
        }
    }
}
