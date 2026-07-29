package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.fixtures.aUser
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * The local user row is a cache and a foreign-key target, not the record of truth: Firestore owns
 * email, workspace membership and the default workspace. These specs pin that asymmetry down —
 * it is the kind of thing a "just add the column" change would quietly break.
 */
class UserRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var repository: UserRepositoryImpl

    beforeEach {
        database = inMemoryLocalDatabase()
        repository = UserRepositoryImpl(dao = database.userDao())
    }

    afterEach { database.close() }

    "the locally-owned fields survive a round trip" {
        repository.insert(aUser(displayName = "Ada", isAnon = false))

        val stored = repository.getById(userId())!!
        stored.id shouldBe userId()
        stored.displayName shouldBe "Ada"
        stored.isAnon shouldBe false
    }

    // Room has no column for these; reading them back as anything but empty would mean the local
    // cache had started competing with Firestore for ownership.
    "the remote-owned fields come back empty rather than stale" {
        repository.insert(
            aUser(
                email = "ada@example.com",
                workspaceIds = listOf(workspaceId()),
                defaultWorkspaceId = workspaceId(),
            ),
        )

        val stored = repository.getById(userId())!!
        stored.email.shouldBeNull()
    }

    "an anonymous user keeps its flag" {
        repository.insert(aUser(displayName = null, isAnon = true))

        val stored = repository.getById(userId())!!
        stored.displayName.shouldBeNull()
        stored.isAnon shouldBe true
    }

    "an update replaces the display name of an existing row" {
        repository.insert(aUser(displayName = "Ada"))

        repository.update(aUser(displayName = "Ada L."))

        repository.getById(userId())?.displayName shouldBe "Ada L."
    }

    "an upsert inserts when the row is new and overwrites when it is not" {
        repository.upsert(aUser(displayName = "First"))
        repository.upsert(aUser(displayName = "Second"))

        repository.getAll().first().map { it.displayName } shouldContainExactly listOf("Second")
    }

    "a delete removes the row" {
        repository.insert(aUser())

        repository.delete(userId())

        repository.getById(userId()).shouldBeNull()
        repository.getAll().first() shouldContainExactly emptyList()
    }

    "reading a user that was never stored is null, not a failure" {
        repository.getById(userId("nobody")).shouldBeNull()
    }
})
