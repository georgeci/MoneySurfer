package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.data.remote.CategoryDoc
import com.georgeci.moneysurfer.data.remote.TransactionDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceInviteDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceMemberDoc
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

class TombstonePatchSpec : StringSpec({

    "patch stamps deletedAt and updatedAt from the mutation enqueue time" {
        val patch = tombstonePatchFor(
            aDeleteMutation(createdAtMillis = 1_700_000_111_222),
            clientVersionCode = 42,
        )

        patch shouldBe TombstonePatch(
            deletedAt = 1_700_000_111_222,
            updatedAt = 1_700_000_111_222,
            clientVersionCode = 42,
        )
    }

    "retrying the same mutation produces an identical patch" {
        val mutation = aDeleteMutation(createdAtMillis = 1_700_000_000_000)

        val first = tombstonePatchFor(mutation, clientVersionCode = 7)
        val retried = tombstonePatchFor(mutation.copy(attempts = 3, lastError = "boom"), clientVersionCode = 7)

        retried shouldBe first
    }

    // Simulates the Firestore field-mask update: the patch's fields overlaid on
    // an existing doc must decode as a tombstone through every entity serializer
    // the pull path uses. Catches field-name drift between TombstonePatch and
    // the DTOs in RemoteDtos.kt.
    "patch fields overlaid on each entity doc decode as a pull-side tombstone" {
        val patch = tombstonePatchFor(
            aDeleteMutation(createdAtMillis = 1_700_000_222_333),
            clientVersionCode = 5,
        )

        fun <T> overlayAndDecode(serializer: KSerializer<T>, doc: T): T {
            val original = json.encodeToJsonElement(serializer, doc).jsonObject
            val patched = json.encodeToJsonElement(TombstonePatch.serializer(), patch).jsonObject
            return json.decodeFromJsonElement(serializer, JsonObject(original + patched))
        }

        overlayAndDecode(AccountDoc.serializer(), AccountDoc(updatedAt = 1)) shouldBe
            AccountDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
        overlayAndDecode(CategoryDoc.serializer(), CategoryDoc(updatedAt = 1)) shouldBe
            CategoryDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
        overlayAndDecode(TransactionDoc.serializer(), TransactionDoc(updatedAt = 1)) shouldBe
            TransactionDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
        overlayAndDecode(WorkspaceDoc.serializer(), WorkspaceDoc(updatedAt = 1)) shouldBe
            WorkspaceDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
        overlayAndDecode(WorkspaceMemberDoc.serializer(), WorkspaceMemberDoc(updatedAt = 1)) shouldBe
            WorkspaceMemberDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
        overlayAndDecode(WorkspaceInviteDoc.serializer(), WorkspaceInviteDoc(updatedAt = 1)) shouldBe
            WorkspaceInviteDoc(deletedAt = 1_700_000_222_333, updatedAt = 1_700_000_222_333, clientVersionCode = 5)
    }
})

private val json = Json { encodeDefaults = true }

private fun aDeleteMutation(createdAtMillis: Long): PendingMutation = PendingMutation(
    id = "m-1",
    entityType = "TRANSACTION",
    entityId = "t-1",
    operation = MutationOperation.DELETE,
    scopeKey = "ws-1",
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
    attempts = 0,
    lastError = null,
)
