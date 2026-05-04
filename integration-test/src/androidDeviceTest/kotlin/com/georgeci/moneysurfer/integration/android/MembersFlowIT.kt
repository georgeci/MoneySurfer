package com.georgeci.moneysurfer.integration.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the members sync flow and Firestore rules.
 *
 * Scenarios:
 *  1. [pull_creates_user_stub_for_unknown_member] — WorkspaceMemberSyncPlugin.applyDoc calls
 *     userDao.insertIgnore before upsertMember; verifies no SQLite FK 787 when the pulled
 *     member's UserEntity is absent from local Room.
 *  2. [invitee_can_create_own_member_row] — Firestore rule `request.auth.uid == uid` lets an
 *     invitee write their own member doc (accept-invite path). No owner involvement required.
 *  3. [member_can_self_leave_via_outbox] — markLeft() updates Room + enqueues outbox mutation
 *     with status=LEFT; upload succeeds because the rule allows `status in ["ACTIVE","LEFT"]`
 *     when role is unchanged.
 *  4. [member_self_role_change_is_denied] — Firestore rule forbids self-update that changes
 *     `role`; verifies privilege-escalation (VIEWER → EDITOR) is rejected.
 *
 * Pre-req: Firebase Emulator Suite running locally:
 *
 *     firebase emulators:start --only firestore,auth --project demo-moneysurfer
 *
 * AVD reaches them at 10.0.2.2:8080 / 10.0.2.2:9099.
 */
@RunWith(AndroidJUnit4::class)
class MembersFlowIT {

    private lateinit var ownerHarness: AndroidIntegrationHarness
    private lateinit var memberHarness: AndroidIntegrationHarness

    @Before
    fun setUp() {
        ownerHarness = AndroidIntegrationHarness(appName = "members-owner-it")
        memberHarness = AndroidIntegrationHarness(appName = "members-member-it")
    }

    @After
    fun tearDown() {
        ownerHarness.close()
        memberHarness.close()
    }

    // ── 1. Member pull creates UserEntity stub ────────────────────────────────

    @Test
    fun pull_creates_user_stub_for_unknown_member() = runTest {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-mem-pull-owner-$tag@example.com"
        val memberEmail = "it-mem-pull-member-$tag@example.com"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val memberCreds = memberHarness.env.auth
            .createUserWithEmailAndPassword(memberEmail, "pwd-$tag")
        val memberUid = memberCreds.user!!.uid

        val workspaceId = WorkspaceId("it-mem-pull-ws-$tag")
        val now = System.currentTimeMillis()
        val fs = ownerHarness.env.firestore

        // Seed workspace + two member docs on Firestore (owner auth → isMember gate).
        fs.collection("workspaces").document(workspaceId.value).set(
            workspaceMap("Members Pull IT $tag", ownerUid, now),
        )
        fs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid)
            .set(memberMap(ownerUid, ownerEmail, "OWNER", now))
        fs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(memberUid)
            .set(memberMap(memberUid, memberEmail, "EDITOR", now))

        // Seed only the owner's UserEntity + workspace locally.
        // memberUid is intentionally NOT pre-seeded — WorkspaceMemberSyncPlugin
        // must create it via userDao.insertIgnore (FK: userId → users).
        with(ownerHarness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "IT Owner", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Members Pull IT $tag",
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

        // Seed remote user doc + workspace ref so AllUserData pull resolves workspaceIds.
        ownerHarness.userRemoteRepository.create(
            uid = ownerUid,
            displayName = "IT Owner",
            email = ownerEmail,
            isAnon = false,
            createdAt = now,
        )
        ownerHarness.userRemoteRepository.addWorkspaceRef(ownerUid, workspaceId)

        ownerHarness.session.currentFirebaseUid.set(ownerUid)
        ownerHarness.session.currentUserId.set(UserId(ownerUid))
        ownerHarness.session.currentWorkspaceId.set(workspaceId)

        val pullResult = ownerHarness.pullRemoteChanges(
            scope = SyncScope.AllUserData,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("AllUserData pull should succeed") {
            pullResult.shouldBeInstanceOf<Either.Right<*>>()
        }

        // Both member rows must have landed in Room.
        val members = ownerHarness.database.workspaceMemberDao()
            .getByWorkspaceId(workspaceId.value).first()
        withClue("both members should be in Room after pull") {
            members shouldHaveSize 2
        }

        // The second member's UserEntity stub must have been auto-created by insertIgnore.
        val memberUser = ownerHarness.database.userDao().getById(memberUid)
        withClue("WorkspaceMemberSyncPlugin must have stubbed the second member's UserEntity") {
            memberUser shouldNotBe null
        }
    }

    // ── 2. Invitee creates own member row ─────────────────────────────────────

    /**
     * Firestore rule `request.auth.uid == uid` on the members sub-collection allows the
     * invitee to write their own member doc. This is the accept-invite happy path.
     */
    @Test
    fun invitee_can_create_own_member_row() = runTest {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-mem-create-owner-$tag@example.com"
        val inviteeEmail = "it-mem-create-invitee-$tag@example.com"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val inviteeCreds = memberHarness.env.auth
            .createUserWithEmailAndPassword(inviteeEmail, "pwd-$tag")
        val inviteeUid = inviteeCreds.user!!.uid

        val workspaceId = WorkspaceId("it-mem-create-ws-$tag")
        val now = System.currentTimeMillis()
        val ownerFs = ownerHarness.env.firestore

        // Owner creates workspace + own member row.
        ownerFs.collection("workspaces").document(workspaceId.value).set(
            workspaceMap("Members Create IT $tag", ownerUid, now),
        )
        ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid)
            .set(memberMap(ownerUid, ownerEmail, "OWNER", now))

        // Invitee writes their own member row (mirrors AcceptInviteUseCase).
        // Signed-in as inviteeUid; doc id == inviteeUid → rule `request.auth.uid == uid`.
        val inviteeFs = memberHarness.env.firestore
        inviteeFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(inviteeUid)
            .set(memberMap(inviteeUid, inviteeEmail, "EDITOR", now))

        // Verify the row is visible to the owner (exercising isMember read rule).
        val snap = ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(inviteeUid).get()
        withClue("invitee member doc should exist on Firestore after self-create") {
            snap.exists shouldBe true
        }
        withClue("role should be EDITOR") {
            snap.get("role") as? String shouldBe "EDITOR"
        }
        withClue("status should be ACTIVE") {
            snap.get("status") as? String shouldBe "ACTIVE"
        }
    }

    // ── 3. Member self-leave via outbox ───────────────────────────────────────

    @Test
    fun member_can_self_leave_via_outbox() = runTest {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-mem-leave-owner-$tag@example.com"
        val memberEmail = "it-mem-leave-member-$tag@example.com"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val memberCreds = memberHarness.env.auth
            .createUserWithEmailAndPassword(memberEmail, "pwd-$tag")
        val memberUid = memberCreds.user!!.uid

        val workspaceId = WorkspaceId("it-mem-leave-ws-$tag")
        val now = System.currentTimeMillis()
        val ownerFs = ownerHarness.env.firestore

        // Seed Firestore: workspace + owner + member (EDITOR).
        ownerFs.collection("workspaces").document(workspaceId.value).set(
            workspaceMap("Self-Leave IT $tag", ownerUid, now),
        )
        ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid)
            .set(memberMap(ownerUid, ownerEmail, "OWNER", now))
        ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(memberUid)
            .set(memberMap(memberUid, memberEmail, "EDITOR", now))

        // Seed member's local Room so markLeft() can read the current row.
        with(memberHarness.database) {
            userDao().upsert(UserEntity(id = ownerUid, displayName = "IT Owner", isAnon = false))
            userDao().upsert(UserEntity(id = memberUid, displayName = "IT Member", isAnon = false))
            workspaceDao().insertAll(
                listOf(
                    WorkspaceEntity(
                        id = workspaceId.value,
                        name = "Self-Leave IT $tag",
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
                    userId = memberUid,
                    workspaceId = workspaceId.value,
                    role = "EDITOR",
                    status = "ACTIVE",
                    displayName = "IT Member",
                    email = memberEmail,
                    addedByUserId = ownerUid,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        memberHarness.session.currentFirebaseUid.set(memberUid)
        memberHarness.session.currentUserId.set(UserId(memberUid))
        memberHarness.session.currentWorkspaceId.set(workspaceId)

        // markLeft → Room update + outbox enqueue.
        memberHarness.memberRepository.markLeft(
            userId = UserId(memberUid),
            workspaceId = workspaceId,
        )

        // Upload outbox — rule: self can set status=LEFT when role is unchanged.
        val uploadResult = memberHarness.uploadPendingChanges(
            scope = SyncScope.UploadOnly,
            onProgress = {},
            cancelToken = SimpleCancelToken(),
        )
        withClue("member self-leave upload must succeed") {
            uploadResult.shouldBeInstanceOf<Either.Right<*>>()
        }

        // Verify status=LEFT landed on Firestore.
        val snap = ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(memberUid).get()
        withClue("Firestore member status should be LEFT after self-leave") {
            snap.get("status") as? String shouldBe "LEFT"
        }

        // Room must also reflect LEFT.
        val localMember = memberHarness.database.workspaceMemberDao()
            .getById(memberUid, workspaceId.value)
        withClue("local Room member status should be LEFT") {
            localMember?.status shouldBe "LEFT"
        }
    }

    // ── 4. Self role-escalation is denied ────────────────────────────────────

    /**
     * Firestore rule for self-update requires `request.resource.data.role == resource.data.role`.
     * A VIEWER trying to write role=EDITOR must be rejected.
     */
    @Test
    fun member_self_role_change_is_denied() = runTest {
        val tag = System.currentTimeMillis().toString()
        val ownerEmail = "it-mem-role-owner-$tag@example.com"
        val memberEmail = "it-mem-role-member-$tag@example.com"

        val ownerCreds = ownerHarness.env.auth
            .createUserWithEmailAndPassword(ownerEmail, "pwd-$tag")
        val ownerUid = ownerCreds.user!!.uid

        val memberCreds = memberHarness.env.auth
            .createUserWithEmailAndPassword(memberEmail, "pwd-$tag")
        val memberUid = memberCreds.user!!.uid

        val workspaceId = WorkspaceId("it-mem-role-ws-$tag")
        val now = System.currentTimeMillis()
        val ownerFs = ownerHarness.env.firestore

        // Seed Firestore: workspace + owner + member with role=VIEWER.
        ownerFs.collection("workspaces").document(workspaceId.value).set(
            workspaceMap("Role Deny IT $tag", ownerUid, now),
        )
        ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(ownerUid)
            .set(memberMap(ownerUid, ownerEmail, "OWNER", now))
        ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(memberUid)
            .set(memberMap(memberUid, memberEmail, "VIEWER", now))

        // Member tries to escalate own role VIEWER → EDITOR. Must be denied.
        val memberFs = memberHarness.env.firestore
        val denied = runCatching {
            memberFs.collection("workspaces").document(workspaceId.value)
                .collection("members").document(memberUid).update(
                    mapOf(
                        "role" to "EDITOR",
                        "status" to "ACTIVE",
                        "updatedAt" to now + 1,
                        "clientVersionCode" to 1,
                    ),
                )
        }
        withClue("self role escalation must be rejected by Firestore rules") {
            denied.isFailure shouldBe true
        }

        // Verify role was NOT changed on Firestore.
        val snap = ownerFs.collection("workspaces").document(workspaceId.value)
            .collection("members").document(memberUid).get()
        withClue("role must remain VIEWER after denied escalation attempt") {
            snap.get("role") as? String shouldBe "VIEWER"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun workspaceMap(name: String, ownerUid: String, now: Long) = mapOf(
        "name" to name,
        "description" to "",
        "baseCurrency" to "USD",
        "ownerId" to ownerUid,
        "archived" to false,
        "createdAt" to now,
        "updatedAt" to now,
        "clientVersionCode" to 1,
    )

    private fun memberMap(uid: String, email: String, role: String, now: Long) = mapOf(
        "userId" to uid,
        "role" to role,
        "status" to "ACTIVE",
        "displayName" to "IT User",
        "email" to email,
        "addedByUserId" to uid,
        "addedAt" to now,
        "updatedAt" to now,
        "clientVersionCode" to 1,
    )
}
