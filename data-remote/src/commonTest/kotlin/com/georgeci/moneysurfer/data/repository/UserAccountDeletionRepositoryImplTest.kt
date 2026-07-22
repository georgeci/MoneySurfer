package com.georgeci.moneysurfer.data.repository

import arrow.core.Either
import com.georgeci.moneysurfer.data.remote.AccountDeletionRemoteSource
import com.georgeci.moneysurfer.data.remote.RemoteAccessDeniedException
import com.georgeci.moneysurfer.data.remote.RemoteMemberRef
import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Purge-versus-leave decision matrix. The rule the hosted account-deletion page promises:
 * a workspace is destroyed only when the deleting user owns it AND no other member is ACTIVE;
 * anything shared keeps its content and only loses the user's membership.
 */
class UserAccountDeletionRepositoryImplTest : StringSpec({

    "owner with no other members purges the workspace, subcollections before the root doc" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to UID),
            members = mapOf(WS to listOf(active(UID))),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls shouldContainExactly listOf(
            "deleteCollection:$WS/accounts",
            "deleteCollection:$WS/categories",
            "deleteCollection:$WS/transactions",
            "deleteCollection:$WS/invites",
            "deleteCollection:$WS/budgets",
            "deleteCollection:$WS/recurringRules",
            "deleteCollection:$WS/goalContributions",
            "deleteCollection:$WS/goals",
            "deleteCollection:$WS/members",
            "deleteWorkspace:$WS",
            "deleteEmailMapping:$EMAIL",
            "deleteUserDoc:$UID",
        )
    }

    "owner whose co-members all LEFT or were REMOVED still purges" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to UID),
            members = mapOf(
                WS to listOf(
                    active(UID),
                    RemoteMemberRef("other-1", WorkspaceMemberStatus.LEFT),
                    RemoteMemberRef("other-2", WorkspaceMemberStatus.REMOVED),
                ),
            ),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls.contains("deleteWorkspace:$WS") shouldBe true
        remote.calls.none { it.startsWith("markMemberLeft") } shouldBe true
    }

    "owner with another ACTIVE member keeps the workspace and only leaves it" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to UID),
            members = mapOf(WS to listOf(active(UID), active("other-1"))),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls shouldContainExactly listOf(
            "markMemberLeft:$WS/$UID",
            "deleteEmailMapping:$EMAIL",
            "deleteUserDoc:$UID",
        )
        // leftAt must carry a real wall-clock stamp, not a default 0.
        (remote.lastLeftAtMillis ?: 0L) shouldBeGreaterThan 0L
    }

    "non-owner never purges someone else's workspace, even when alone in it" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to "someone-else"),
            members = mapOf(WS to listOf(active(UID))),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls.none { it.startsWith("deleteWorkspace") } shouldBe true
        remote.calls.contains("markMemberLeft:$WS/$UID") shouldBe true
    }

    "an already REMOVED member row is left untouched" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to "someone-else"),
            members = mapOf(
                WS to listOf(RemoteMemberRef(UID, WorkspaceMemberStatus.REMOVED), active("other-1")),
            ),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls.none { it.startsWith("markMemberLeft") } shouldBe true
    }

    "a missing workspace doc is skipped without touching anything" {
        val remote = FakeRemote(workspaceIds = listOf(WS), owners = emptyMap())

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls shouldContainExactly listOf("deleteEmailMapping:$EMAIL", "deleteUserDoc:$UID")
    }

    "a stale workspace ref that denies access is skipped, deletion still completes" {
        val remote = FakeRemote(
            workspaceIds = listOf("stale-ws", WS),
            owners = mapOf(WS to UID),
            members = mapOf(WS to listOf(active(UID))),
            denied = setOf("stale-ws"),
        )

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls.contains("deleteWorkspace:$WS") shouldBe true
        remote.calls.contains("deleteUserDoc:$UID") shouldBe true
    }

    "email mapping pointing at a different uid is preserved" {
        val remote = FakeRemote(emailMappingUid = "someone-else")

        repository(remote).deleteRemoteUserData(UID, EMAIL).shouldBeRight()

        remote.calls shouldContainExactly listOf("deleteUserDoc:$UID")
    }

    "blank email skips the mapping lookup entirely" {
        val remote = FakeRemote()

        repository(remote).deleteRemoteUserData(UID, email = "  ").shouldBeRight()

        remote.calls shouldContainExactly listOf("deleteUserDoc:$UID")
    }

    "email is normalised before the mapping lookup" {
        val remote = FakeRemote()

        repository(remote).deleteRemoteUserData(UID, email = "  User@Example.COM ").shouldBeRight()

        remote.calls shouldContainExactly listOf("deleteEmailMapping:$EMAIL", "deleteUserDoc:$UID")
    }

    "a non-permission failure aborts and surfaces RemoteDataCleanupFailed" {
        val remote = FakeRemote(
            workspaceIds = listOf(WS),
            owners = mapOf(WS to UID),
            members = mapOf(WS to listOf(active(UID))),
            failWith = IllegalStateException("network down"),
        )

        val result = repository(remote).deleteRemoteUserData(UID, EMAIL)

        result.shouldBeInstanceOf<Either.Left<AccountDeletionError.RemoteDataCleanupFailed>>()
        remote.calls.none { it.startsWith("deleteUserDoc") } shouldBe true
    }
})

private const val UID = "uid-1"
private const val WS = "ws-1"
private const val EMAIL = "user@example.com"

private fun active(uid: String) = RemoteMemberRef(uid, WorkspaceMemberStatus.ACTIVE)

private fun repository(remote: FakeRemote) =
    UserAccountDeletionRepositoryImpl(remote = remote, clock = ClockUseCase())

private fun Either<AccountDeletionError, Unit>.shouldBeRight() {
    shouldBeInstanceOf<Either.Right<Unit>>()
}

/** Records every mutating call so tests can assert both the decision and its ordering. */
private class FakeRemote(
    private val workspaceIds: List<String> = emptyList(),
    private val owners: Map<String, String> = emptyMap(),
    private val members: Map<String, List<RemoteMemberRef>> = emptyMap(),
    private val denied: Set<String> = emptySet(),
    private val emailMappingUid: String? = UID,
    private val failWith: Throwable? = null,
) : AccountDeletionRemoteSource {

    val calls = mutableListOf<String>()

    /** `leftAt` stamped on the last soft-leave — the exact value comes from the real clock. */
    var lastLeftAtMillis: Long? = null
        private set

    override suspend fun fetchWorkspaceIds(uid: String) = workspaceIds

    override suspend fun fetchWorkspaceOwnerId(workspaceId: String): String? {
        denyIfNeeded(workspaceId)
        return owners[workspaceId]
    }

    override suspend fun fetchMembers(workspaceId: String): List<RemoteMemberRef> {
        denyIfNeeded(workspaceId)
        return members[workspaceId].orEmpty()
    }

    override suspend fun deleteWorkspaceCollection(workspaceId: String, collectionName: String) {
        failWith?.let { throw it }
        calls += "deleteCollection:$workspaceId/$collectionName"
    }

    override suspend fun deleteWorkspace(workspaceId: String) {
        calls += "deleteWorkspace:$workspaceId"
    }

    override suspend fun markMemberLeft(workspaceId: String, uid: String, nowMillis: Long) {
        lastLeftAtMillis = nowMillis
        calls += "markMemberLeft:$workspaceId/$uid"
    }

    override suspend fun fetchEmailMappingUid(emailKey: String) = emailMappingUid

    override suspend fun deleteEmailMapping(emailKey: String) {
        calls += "deleteEmailMapping:$emailKey"
    }

    override suspend fun deleteUserDoc(uid: String) {
        calls += "deleteUserDoc:$uid"
    }

    private fun denyIfNeeded(workspaceId: String) {
        if (workspaceId in denied) throw RemoteAccessDeniedException("permission denied")
    }
}
