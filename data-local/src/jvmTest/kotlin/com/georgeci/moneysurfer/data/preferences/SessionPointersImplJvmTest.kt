package com.georgeci.moneysurfer.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import java.nio.file.Files

class SessionPointersImplJvmTest : StringSpec({

    fun newStore(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("ms-session-test")
        return createDataStore { dir.resolve("session.preferences_pb").toString() }
    }

    "pointers round-trip through the store" {
        val session = SessionPointersImpl(newStore())

        session.setCurrentUser(UserId("u-1"))
        session.setCurrentWorkspace(WorkspaceId("ws-1"))
        session.setFirebaseUid("fb-1")

        session.currentUserId.first() shouldBe UserId("u-1")
        session.currentWorkspaceId.first() shouldBe WorkspaceId("ws-1")
        session.currentFirebaseUid.first() shouldBe "fb-1"
    }

    "clearing a pointer reads back as null, not as an empty id" {
        val session = SessionPointersImpl(newStore())
        session.setCurrentUser(UserId("u-1"))

        session.setCurrentUser(null)

        session.currentUserId.first() shouldBe null
    }

    "clearSession drops user, workspace and uid together" {
        val session = SessionPointersImpl(newStore())
        session.setCurrentUser(UserId("u-1"))
        session.setCurrentWorkspace(WorkspaceId("ws-1"))
        session.setFirebaseUid("fb-1")

        session.clearSession()

        session.currentUserId.first() shouldBe null
        session.currentWorkspaceId.first() shouldBe null
        session.currentFirebaseUid.first() shouldBe null
    }

    "clearSession leaves hasUsedDemo alone" {
        // Device-scoped on purpose: resetting it would let orphan demo data reach Firestore on the
        // next real sign-in (sync.md §2.11).
        val session = SessionPointersImpl(newStore())
        session.setHasUsedDemo(true)

        session.clearSession()

        session.hasUsedDemo.first() shouldBe true
    }

    "a fresh store reports no session" {
        val session = SessionPointersImpl(newStore())

        session.currentUserId.first() shouldBe null
        session.currentWorkspaceId.first() shouldBe null
        session.currentFirebaseUid.first() shouldBe null
        session.hasUsedDemo.first() shouldBe false
    }
})
