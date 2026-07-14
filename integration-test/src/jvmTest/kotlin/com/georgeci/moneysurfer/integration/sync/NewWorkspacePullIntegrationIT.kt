package com.georgeci.moneysurfer.integration.sync

import arrow.core.Either
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.sync.PullRemoteChangesUseCaseImpl
import com.georgeci.moneysurfer.data.sync.UserWorkspacesProvider
import com.georgeci.moneysurfer.data.sync.WorkspaceCollectionReader
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.internal.repository.SyncMetaRepositoryImpl
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.DeserializationStrategy
import kotlin.time.Instant

/**
 * Simulates "a workspace created on device A arrives on a fresh device B":
 * the remote lists a workspace the local Room has never seen, and
 * `PullRemoteChangesUseCaseImpl` hydrates it — root document first (Room FK),
 * then subcollections — persisting pull cursors in the real sync database.
 *
 * The Firestore reader is an in-memory fake that honours the `sinceMillis`
 * contract; everything below it (pull loop, cursor storage, Room writes)
 * is the production code path.
 */
class NewWorkspacePullIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var remote: InMemoryRemote
    lateinit var stack: PullStack

    beforeEach {
        harness = IntegrationHarness()
        remote = InMemoryRemote()
        stack = PullStack(harness, remote)
    }

    afterEach {
        harness.close()
    }

    "first sync hydrates the new workspace root before its subcollections" {
        remote.workspaces["ws-new"] = rootDoc("ws-new", updatedAt = 100)
        remote.docs["ws-new" to "members"] = listOf(doc("owner-uid", updatedAt = 110))
        remote.docs["ws-new" to "categories"] = listOf(
            doc("cat-1", updatedAt = 120),
            doc("cat-2", updatedAt = 130),
        )

        val result = stack.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 4, conflictCount = 0))
        // The workspace row is now in local Room — the whole point of the first sync.
        harness.database.workspaceDao().getById("ws-new").shouldNotBeNull().name shouldBe "Pulled"
        // Root doc applied before any subcollection doc (Room FK on workspaceId).
        stack.applyOrder.first() shouldBe "WORKSPACE:ws-new"
        stack.applyOrder shouldContainExactly listOf(
            "WORKSPACE:ws-new",
            "WORKSPACE_MEMBER:owner-uid",
            "CATEGORY:cat-1",
            "CATEGORY:cat-2",
        )
        // Cursors persisted per collection in the real sync database.
        stack.syncMeta.cursor("ws-new", "members") shouldBe Instant.fromEpochMilliseconds(110)
        stack.syncMeta.cursor("ws-new", "categories") shouldBe Instant.fromEpochMilliseconds(130)
    }

    "second sync resumes from the Room-persisted cursor and pulls only newer documents" {
        remote.workspaces["ws-new"] = rootDoc("ws-new", updatedAt = 100)
        remote.docs["ws-new" to "categories"] = listOf(doc("cat-1", updatedAt = 120))
        stack.pull(SyncScope.AllUserData).shouldBeInstanceOf<Either.Right<PullSummary>>()

        // A new category appears remotely; an old one stays unchanged.
        remote.docs["ws-new" to "categories"] = listOf(
            doc("cat-1", updatedAt = 120),
            doc("cat-2", updatedAt = 200),
        )
        // Fresh use-case instance over the same sync DB — survives an app restart.
        val restarted = PullStack(harness, remote)

        val result = restarted.pull(SyncScope.AllUserData)

        // Only cat-2 is newer than the stored cursor (120); the root doc re-applies too.
        result shouldBe Either.Right(PullSummary(downloadedCount = 2, conflictCount = 0))
        restarted.applyOrder shouldContainExactly listOf("WORKSPACE:ws-new", "CATEGORY:cat-2")
        restarted.syncMeta.cursor("ws-new", "categories") shouldBe
            Instant.fromEpochMilliseconds(200)
    }

    "failed apply leaves the cursor untouched so the batch is retried on the next sync" {
        remote.workspaces["ws-new"] = rootDoc("ws-new", updatedAt = 100)
        remote.docs["ws-new" to "categories"] = listOf(doc("cat-broken", updatedAt = 120))
        stack.categoriesPlugin.failOnDocId = "cat-broken"

        stack.pull(SyncScope.AllUserData).shouldBeInstanceOf<Either.Left<*>>()
        stack.syncMeta.cursor("ws-new", "categories").shouldBeNull()

        // The transient failure clears; the same document is re-served and re-applied.
        stack.categoriesPlugin.failOnDocId = null
        val retry = stack.pull(SyncScope.AllUserData)

        retry.shouldBeInstanceOf<Either.Right<PullSummary>>()
        stack.categoriesPlugin.applied shouldContainExactly listOf("cat-broken")
        stack.syncMeta.cursor("ws-new", "categories") shouldBe Instant.fromEpochMilliseconds(120)
    }

    "ActiveWorkspace scope pulls the workspace pinned in the session" {
        remote.workspaces["ws-pinned"] = rootDoc("ws-pinned", updatedAt = 50)
        val pinnedStack = PullStack(harness, remote, currentWorkspaceId = "ws-pinned")

        val result = pinnedStack.pull(SyncScope.ActiveWorkspace)

        result shouldBe Either.Right(PullSummary(downloadedCount = 1, conflictCount = 0))
        harness.database.workspaceDao().getById("ws-pinned").shouldNotBeNull()
    }
})

// ── Wiring ───────────────────────────────────────────────────────────────────

private class PullStack(
    harness: IntegrationHarness,
    remote: InMemoryRemote,
    currentWorkspaceId: String? = null,
) {
    val applyOrder = mutableListOf<String>()
    val syncMeta = SyncMetaRepositoryImpl(harness.syncDatabase.syncMetaDao())

    /** Mirrors the production WorkspaceSyncPlugin: stub owner user, upsert workspace row. */
    private val rootPlugin = object : SyncEntityPlugin {
        override val entityType: String = "WORKSPACE"
        override val firestoreCollectionName: String? = null
        override val pullPriority: Int = 0

        override suspend fun push(mutation: PendingMutation) = error("pull-only test")

        override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
            harness.database.userDao().insertIgnore(
                UserEntity(id = "owner-uid", displayName = null, isAnon = false),
            )
            harness.database.workspaceDao().upsertAll(
                listOf(
                    WorkspaceEntity(
                        id = doc.id,
                        name = "Pulled",
                        description = "",
                        baseCurrency = "USD",
                        ownerId = "owner-uid",
                        createdAt = 0L,
                        archived = false,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                    ),
                ),
            )
            applyOrder += "$entityType:${doc.id}"
            return EntityApplyResult(applied = true, wasConflict = false)
        }
    }

    private val membersPlugin = RecordingApplyPlugin(
        entityType = "WORKSPACE_MEMBER",
        collectionName = "members",
        pullPriority = 10,
        applyOrder = applyOrder,
    )
    val categoriesPlugin = RecordingApplyPlugin(
        entityType = "CATEGORY",
        collectionName = "categories",
        pullPriority = 20,
        applyOrder = applyOrder,
    )

    private val useCase = PullRemoteChangesUseCaseImpl(
        collectionReader = remote,
        syncMeta = syncMeta,
        plugins = listOf(categoriesPlugin, membersPlugin, rootPlugin),
        session = InMemorySessionPointers(
            currentWorkspaceId = currentWorkspaceId?.let(::WorkspaceId),
            currentFirebaseUid = "firebase-uid-1",
        ),
        userWorkspacesProvider = object : UserWorkspacesProvider {
            override suspend fun workspaceIds(): List<String> = remote.workspaces.keys.toList()
            override suspend fun invitedWorkspaceIds(): List<String> = emptyList()
        },
    )

    suspend fun pull(scope: SyncScope) = useCase(scope, {}, SimpleCancelToken())
}

/** In-memory stand-in for Firestore that honours the `updatedAt > sinceMillis` contract. */
private class InMemoryRemote : WorkspaceCollectionReader {
    val workspaces = mutableMapOf<String, RemoteDocument>()
    val docs = mutableMapOf<Pair<String, String>, List<RemoteDocument>>()

    override suspend fun fetchWorkspaceDoc(workspaceId: String): RemoteDocument? =
        workspaces[workspaceId]

    override suspend fun fetchUpdatedSince(
        workspaceId: String,
        collectionName: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> = docs[workspaceId to collectionName].orEmpty()
        .filter { (it.getLong("updatedAt") ?: 0L) > sinceMillis }
        .sortedBy { it.getLong("updatedAt") ?: 0L }
        .take(limit)

    override suspend fun fetchInvitesForUser(
        workspaceId: String,
        uid: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> = emptyList()
}

private fun rootDoc(id: String, updatedAt: Long): RemoteDocument = FakeDoc(id, updatedAt)
private fun doc(id: String, updatedAt: Long): RemoteDocument = FakeDoc(id, updatedAt)

private class FakeDoc(
    override val id: String,
    private val updatedAt: Long,
) : RemoteDocument {
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T =
        error("test plugins do not decode documents")

    override fun getLong(field: String): Long? = if (field == "updatedAt") updatedAt else null
}

private class RecordingApplyPlugin(
    override val entityType: String,
    private val collectionName: String,
    override val pullPriority: Int,
    private val applyOrder: MutableList<String>,
) : SyncEntityPlugin {
    override val firestoreCollectionName: String get() = collectionName

    val applied = mutableListOf<String>()
    var failOnDocId: String? = null

    override suspend fun push(mutation: PendingMutation) = error("pull-only test")

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        if (doc.id == failOnDocId) error("simulated apply failure for ${doc.id}")
        applied += doc.id
        applyOrder += "$entityType:${doc.id}"
        return EntityApplyResult(applied = true, wasConflict = false)
    }
}
