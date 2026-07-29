package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.remote.UserDoc
import com.georgeci.moneysurfer.data.remote.UserEmailDoc
import com.georgeci.moneysurfer.data.remote.UserRemoteSource
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private const val UID = "uid-1"

private class FakeUserRemoteSource : UserRemoteSource {

    val users: MutableMap<String, UserDoc> = mutableMapOf()
    val emailMappings: MutableMap<String, UserEmailDoc> = mutableMapOf()
    val writes: MutableList<String> = mutableListOf()

    override suspend fun fetchUser(uid: String): UserDoc? = users[uid]

    override suspend fun createUser(uid: String, doc: UserDoc) {
        users[uid] = doc
        writes += "createUser($uid)"
    }

    override suspend fun addWorkspaceRef(uid: String, workspaceId: String) {
        writes += "addWorkspaceRef($uid,$workspaceId)"
    }

    override suspend fun setDefaultWorkspace(uid: String, workspaceId: String) {
        writes += "setDefaultWorkspace($uid,$workspaceId)"
    }

    override suspend fun addInvitedWorkspaceRef(uid: String, workspaceId: String) {
        writes += "addInvitedWorkspaceRef($uid,$workspaceId)"
    }

    override suspend fun removeInvitedWorkspaceRef(uid: String, workspaceId: String) {
        writes += "removeInvitedWorkspaceRef($uid,$workspaceId)"
    }

    override suspend fun fetchEmailMapping(emailKey: String): UserEmailDoc? = emailMappings[emailKey]

    override suspend fun upsertEmailMapping(emailKey: String, doc: UserEmailDoc) {
        emailMappings[emailKey] = doc
        writes += "upsertEmailMapping($emailKey)"
    }
}

private fun repository(source: UserRemoteSource) = UserRemoteRepositoryImpl(
    remoteSource = source,
    clock = ClockUseCase(
        object : Clock {
            override fun now(): Instant = NOW
        },
    ),
)

/**
 * The email→uid mapping is what invite delivery resolves a recipient through, so the key it is
 * stored and looked up under has to be derived identically on both paths — a user who typed their
 * address with a capital letter when signing up must still be findable by an inviter who typed it
 * in lower case.
 */
class UserRemoteRepositoryImplTest : StringSpec({

    "a stored user document becomes the domain user, refs included" {
        runTest {
            val source = FakeUserRemoteSource()
            source.users[UID] = UserDoc(
                displayName = "Ada",
                email = "ada@example.com",
                isAnon = false,
                createdAt = 1L,
                workspaceIds = listOf("ws-1", "ws-2"),
                defaultWorkspaceId = "ws-1",
                invitedWorkspaceIds = listOf("ws-3"),
            )

            val user = repository(source).fetch(UID)!!

            user.id shouldBe UserId(UID)
            user.displayName shouldBe "Ada"
            user.email shouldBe "ada@example.com"
            user.workspaceIds shouldContainExactly listOf(WorkspaceId("ws-1"), WorkspaceId("ws-2"))
            user.defaultWorkspaceId shouldBe WorkspaceId("ws-1")
            user.invitedWorkspaceIds shouldContainExactly listOf(WorkspaceId("ws-3"))
        }
    }

    "a user that has no remote document reads as null" {
        runTest {
            repository(FakeUserRemoteSource()).fetch(UID).shouldBeNull()
        }
    }

    "a created user starts with no workspace refs" {
        runTest {
            val source = FakeUserRemoteSource()

            repository(source).create(
                uid = UID,
                displayName = "Ada",
                email = "ada@example.com",
                isAnon = false,
                createdAt = 99L,
            )

            source.users.getValue(UID) shouldBe UserDoc(
                displayName = "Ada",
                email = "ada@example.com",
                isAnon = false,
                createdAt = 99L,
            )
        }
    }

    "the workspace-ref writers pass the raw ids through" {
        runTest {
            val source = FakeUserRemoteSource()
            val repository = repository(source)

            repository.addWorkspaceRef(UID, WorkspaceId("ws-1"))
            repository.setDefaultWorkspace(UID, WorkspaceId("ws-1"))
            repository.addInvitedWorkspaceRef(UID, WorkspaceId("ws-2"))
            repository.removeInvitedWorkspaceRef(UID, WorkspaceId("ws-2"))

            source.writes shouldContainExactly listOf(
                "addWorkspaceRef($UID,ws-1)",
                "setDefaultWorkspace($UID,ws-1)",
                "addInvitedWorkspaceRef($UID,ws-2)",
                "removeInvitedWorkspaceRef($UID,ws-2)",
            )
        }
    }

    "an email is written under its normalized key, with the clock's moment" {
        runTest {
            val source = FakeUserRemoteSource()

            repository(source).upsertEmailMapping(email = "  Ada@Example.COM ", uid = UID)

            source.emailMappings shouldBe mapOf(
                "ada@example.com" to UserEmailDoc(uid = UID, updatedAt = NOW.toEpochMilliseconds()),
            )
        }
    }

    "a lookup normalizes the address the same way the write did" {
        runTest {
            val source = FakeUserRemoteSource()
            source.emailMappings["ada@example.com"] = UserEmailDoc(uid = UID, updatedAt = 1L)

            repository(source).findByEmail("  ADA@example.com  ") shouldBe UserId(UID)
        }
    }

    "an address nobody has registered reads as no user" {
        runTest {
            repository(FakeUserRemoteSource()).findByEmail("nobody@example.com").shouldBeNull()
        }
    }

    "a blank address is not even looked up" {
        runTest {
            val source = FakeUserRemoteSource()

            repository(source).findByEmail("   ").shouldBeNull()

            source.writes shouldContainExactly emptyList()
        }
    }

    // A half-written mapping would otherwise resolve to `UserId("")`, and the invite would be
    // addressed to a user that cannot exist.
    "a mapping with an empty uid is ignored rather than trusted" {
        runTest {
            val source = FakeUserRemoteSource()
            source.emailMappings["ada@example.com"] = UserEmailDoc(uid = "", updatedAt = 1L)

            repository(source).findByEmail("ada@example.com").shouldBeNull()
        }
    }

    "a mapping is not written for a blank address or a blank uid" {
        runTest {
            val source = FakeUserRemoteSource()
            val repository = repository(source)

            repository.upsertEmailMapping(email = "   ", uid = UID)
            repository.upsertEmailMapping(email = "ada@example.com", uid = "")

            source.emailMappings shouldBe emptyMap()
            source.writes shouldContainExactly emptyList()
        }
    }
})
