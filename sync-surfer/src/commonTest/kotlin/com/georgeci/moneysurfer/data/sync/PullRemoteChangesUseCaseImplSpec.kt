package com.georgeci.moneysurfer.data.sync

import arrow.core.Either
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullSummary
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCancelledException
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.DeserializationStrategy
import kotlin.time.Instant

class PullRemoteChangesUseCaseImplSpec : StringSpec({

    "UploadOnly scope pulls nothing" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))

        val result = env.pull(SyncScope.UploadOnly)

        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.reader.fetchCalls.shouldBeEmpty()
    }

    "ActiveWorkspace with no current workspace pulls nothing" {
        val env = PullEnv(currentWorkspaceId = null, providerWorkspaceIds = listOf("ws-1"))

        val result = env.pull(SyncScope.ActiveWorkspace)

        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.reader.fetchCalls.shouldBeEmpty()
    }

    "ActiveWorkspace pulls only the current workspace even when the provider knows more" {
        val env = PullEnv(
            currentWorkspaceId = "ws-active",
            providerWorkspaceIds = listOf("ws-active", "ws-other"),
        )
        env.reader.collectionDocs["ws-active" to "accounts"] = listOf(doc("a-1", updatedAt = 10))
        env.reader.collectionDocs["ws-other" to "accounts"] = listOf(doc("a-2", updatedAt = 10))

        val result = env.pull(SyncScope.ActiveWorkspace)

        result shouldBe Either.Right(PullSummary(downloadedCount = 1, conflictCount = 0))
        env.accountsPlugin.applied.map { it.second }.toSet() shouldBe setOf("ws-active")
    }

    "AllUserData pulls every workspace the remote lists for the user" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1", "ws-2"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(doc("a-1", updatedAt = 10))
        env.reader.collectionDocs["ws-2" to "accounts"] = listOf(doc("a-2", updatedAt = 20))

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 2, conflictCount = 0))
        env.accountsPlugin.applied.map { it.first } shouldContainExactly listOf("a-1", "a-2")
    }

    "AllUserData does NOT fall back to the local workspace when the provider is empty" {
        // A full sync must only pull what the remote says the user owns — pulling the
        // locally-pinned wid can hit PERMISSION_DENIED when the user is just an invitee.
        val env = PullEnv(currentWorkspaceId = "ws-local", providerWorkspaceIds = emptyList())

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.reader.fetchCalls.shouldBeEmpty()
    }

    "ChangedSinceLastSync falls back to the active workspace when the provider is empty" {
        val env = PullEnv(currentWorkspaceId = "ws-local", providerWorkspaceIds = emptyList())
        env.reader.collectionDocs["ws-local" to "accounts"] = listOf(doc("a-1", updatedAt = 5))

        val result = env.pull(SyncScope.ChangedSinceLastSync)

        result shouldBe Either.Right(PullSummary(downloadedCount = 1, conflictCount = 0))
        env.accountsPlugin.applied.map { it.second } shouldContainExactly listOf("ws-local")
    }

    "workspace root document is applied before subcollection plugins" {
        // Subcollection rows carry a Room FK on workspaceId — the root doc must land first.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-new"))
        env.reader.rootDocs["ws-new"] = doc("ws-new", updatedAt = 1)
        env.reader.collectionDocs["ws-new" to "accounts"] = listOf(doc("a-1", updatedAt = 10))

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 2, conflictCount = 0))
        env.applyOrder shouldContainExactly listOf("WORKSPACE:ws-new", "ACCOUNT:a-1")
    }

    "missing workspace root document is not counted and does not fail the pull" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-ghost"))

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.rootPlugin.applied.shouldBeEmpty()
    }

    "first pull starts from cursor 0 and advances the cursor to the newest doc" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(
            doc("a-1", updatedAt = 100),
            doc("a-2", updatedAt = 300),
            doc("a-3", updatedAt = 200),
        )

        env.pull(SyncScope.AllUserData)

        env.reader.fetchCalls.filter { it.collection == "accounts" } shouldContainExactly listOf(
            FetchCall(workspaceId = "ws-1", collection = "accounts", sinceMillis = 0),
        )
        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe Instant.fromEpochMilliseconds(300)
    }

    "second pull passes the stored cursor to the reader" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.syncMeta.cursors["ws-1" to "accounts"] = Instant.fromEpochMilliseconds(500)

        env.pull(SyncScope.AllUserData)

        env.reader.fetchCalls.filter { it.collection == "accounts" } shouldContainExactly listOf(
            FetchCall(workspaceId = "ws-1", collection = "accounts", sinceMillis = 500),
        )
    }

    "empty batch keeps the cursor untouched but still marks success" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.syncMeta.cursors["ws-1" to "accounts"] = Instant.fromEpochMilliseconds(500)

        env.pull(SyncScope.AllUserData)

        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe Instant.fromEpochMilliseconds(500)
        env.syncMeta.successMarks.filter { it.second == "accounts" } shouldContainExactly
            listOf("ws-1" to "accounts")
        env.syncMeta.attemptMarks.filter { it.second == "accounts" } shouldContainExactly
            listOf("ws-1" to "accounts")
    }

    "conflicts and skipped docs are reflected in the summary" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(
            doc("a-applied", updatedAt = 1),
            doc("a-conflict", updatedAt = 2),
            doc("a-skipped", updatedAt = 3),
        )
        env.accountsPlugin.resultOverrides["a-conflict"] =
            EntityApplyResult(applied = true, wasConflict = true)
        env.accountsPlugin.resultOverrides["a-skipped"] =
            EntityApplyResult(applied = false, wasConflict = false)

        val result = env.pull(SyncScope.AllUserData)

        // a-skipped still advances the cursor but is not counted as downloaded.
        result shouldBe Either.Right(PullSummary(downloadedCount = 2, conflictCount = 1))
        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe Instant.fromEpochMilliseconds(3)
    }

    "progress is reported per document with collection name and position" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(
            doc("a-1", updatedAt = 1),
            doc("a-2", updatedAt = 2),
        )
        val progress = mutableListOf<PullProgress>()

        env.pull(SyncScope.AllUserData, onProgress = { progress += it })

        progress shouldContainExactly listOf(
            PullProgress(collection = "accounts", current = 1, total = 2),
            PullProgress(collection = "accounts", current = 2, total = 2),
        )
    }

    "plugin failure surfaces as a sync error and leaves the cursor untouched" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(doc("a-bad", updatedAt = 42))
        env.accountsPlugin.failOnDocId = "a-bad"

        val result = env.pull(SyncScope.AllUserData)

        val left = result.shouldBeInstanceOf<Either.Left<SyncError>>()
        left.value.shouldBeInstanceOf<SyncError.Unknown>()
        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe null
        env.syncMeta.successMarks.filter { it.second == "accounts" }.shouldBeEmpty()
    }

    "cancellation between documents aborts the pull" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        val token = SimpleCancelToken()
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(
            doc("a-1", updatedAt = 1),
            doc("a-2", updatedAt = 2),
        )
        env.accountsPlugin.onApply = { docId -> if (docId == "a-1") token.cancel() }

        shouldThrow<SyncCancelledException> {
            env.pull(SyncScope.AllUserData, cancelToken = token)
        }

        env.accountsPlugin.applied.map { it.first } shouldContainExactly listOf("a-1")
    }

    // -- Draining across batches (issue #342) ------------------------------------------------

    "a collection larger than one batch is drained by a single pull" {
        // Before this, one pull moved at most BATCH_SIZE docs and the rest trickled in through
        // the 1-minute ticker — a real account showed wrong balances for tens of minutes.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] =
            (1..250).map { doc("a-$it", updatedAt = it.toLong()) }

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 250, conflictCount = 0))
        env.accountsPlugin.applied shouldHaveSize 250
        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe Instant.fromEpochMilliseconds(250)
        // 100 + 100 + 50: the short third batch ends the loop.
        env.reader.fetchCalls.filter { it.collection == "accounts" }
            .map { it.sinceMillis } shouldContainExactly listOf(0L, 100L, 200L)
    }

    "a full batch of same-timestamp documents terminates the loop rather than re-reading itself" {
        // The query is a strict `updatedAt > cursor`, so once the cursor lands on the shared
        // timestamp the follow-up query comes back empty and the loop ends. Whatever did not fit
        // in the first batch stays invisible — a pre-existing consequence of the strict cursor,
        // not something the drain loop introduced (see md/test_debt.md §6).
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.collectionDocs["ws-1" to "accounts"] =
            (1..150).map { doc("a-$it", updatedAt = 7) }

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 100, conflictCount = 0))
        env.reader.fetchCalls.filter { it.collection == "accounts" }
            .map { it.sinceMillis } shouldContainExactly listOf(0L, 7L)
        env.syncMeta.cursors["ws-1" to "accounts"] shouldBe Instant.fromEpochMilliseconds(7)
    }

    "cancellation between batches aborts the pull" {
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        val token = SimpleCancelToken()
        env.reader.collectionDocs["ws-1" to "accounts"] =
            (1..150).map { doc("a-$it", updatedAt = it.toLong()) }
        env.accountsPlugin.onApply = { docId -> if (docId == "a-100") token.cancel() }

        shouldThrow<SyncCancelledException> {
            env.pull(SyncScope.AllUserData, cancelToken = token)
        }

        // The first batch completed and its cursor landed; the second was never requested.
        env.reader.fetchCalls.filter { it.collection == "accounts" } shouldHaveSize 1
    }

    // -- Stale workspace refs (issue #342) ---------------------------------------------------

    "an unreadable workspace is skipped instead of failing the whole pull" {
        // A `users/{uid}.workspaceIds` entry whose workspaces/{wid} document was never created
        // reads as PERMISSION_DENIED. Raising here used to make sign-in impossible for the
        // entire account.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-stale", "ws-ok"))
        env.reader.denyWorkspaces += "ws-stale"
        env.reader.collectionDocs["ws-stale" to "accounts"] = listOf(doc("a-never", updatedAt = 1))
        env.reader.collectionDocs["ws-ok" to "accounts"] = listOf(doc("a-1", updatedAt = 10))

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 1, conflictCount = 0))
        env.accountsPlugin.applied shouldContainExactly listOf("a-1" to "ws-ok")
    }

    "a denied subcollection query skips the rest of that workspace" {
        // The other SDK shape: the root `get()` returns nothing rather than throwing, and the
        // denial surfaces on the first subcollection query instead.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-stale"))
        env.reader.denyCollections += "ws-stale" to "invites"
        env.reader.collectionDocs["ws-stale" to "accounts"] = listOf(doc("a-1", updatedAt = 10))

        val result = env.pull(SyncScope.AllUserData)

        // invites (priority 5) is denied, so accounts (priority 10) is never reached.
        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.accountsPlugin.applied.shouldBeEmpty()
    }

    "a network failure aborts the pull instead of passing as a stale ref" {
        // Only a denial means "this workspace will never be readable". A dropped connection
        // means the data is not here *yet* — swallowing it would report a successful sync that
        // downloaded nothing, and the UI would claim the user is up to date.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1"))
        env.reader.failWorkspacesWith["ws-1"] = "UNAVAILABLE: backend unreachable"

        val result = env.pull(SyncScope.AllUserData)

        result.shouldBeInstanceOf<Either.Left<SyncError>>()
    }

    "a local apply failure still aborts the pull" {
        // Tolerance is for unreadable *remote* workspaces only — a plugin that cannot write to
        // Room is a real error and must not be swallowed as a stale ref.
        val env = PullEnv(providerWorkspaceIds = listOf("ws-1", "ws-2"))
        env.reader.collectionDocs["ws-1" to "accounts"] = listOf(doc("a-bad", updatedAt = 42))
        env.accountsPlugin.failOnDocId = "a-bad"

        val result = env.pull(SyncScope.AllUserData)

        result.shouldBeInstanceOf<Either.Left<SyncError>>()
    }

    "AllUserData additionally pulls invites from workspaces the user is invited to" {
        val env = PullEnv(
            firebaseUid = "uid-7",
            providerWorkspaceIds = emptyList(),
            invitedWorkspaceIds = listOf("ws-invited"),
        )
        env.reader.inviteDocs["ws-invited" to "uid-7"] = listOf(doc("inv-1", updatedAt = 9))

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 1, conflictCount = 0))
        env.invitesPlugin.applied shouldContainExactly listOf("inv-1" to "ws-invited")
        env.syncMeta.cursors["ws-invited" to "invites"].shouldNotBeNull()
    }

    "ActiveWorkspace scope skips the invited-workspaces phase" {
        val env = PullEnv(
            firebaseUid = "uid-7",
            currentWorkspaceId = "ws-active",
            invitedWorkspaceIds = listOf("ws-invited"),
        )
        env.reader.inviteDocs["ws-invited" to "uid-7"] = listOf(doc("inv-1", updatedAt = 9))

        env.pull(SyncScope.ActiveWorkspace)

        env.reader.inviteFetchCalls.shouldBeEmpty()
    }

    "invited-workspaces phase is skipped without a Firebase uid" {
        val env = PullEnv(
            firebaseUid = null,
            providerWorkspaceIds = emptyList(),
            invitedWorkspaceIds = listOf("ws-invited"),
        )

        val result = env.pull(SyncScope.AllUserData)

        result shouldBe Either.Right(PullSummary(downloadedCount = 0, conflictCount = 0))
        env.reader.inviteFetchCalls.shouldBeEmpty()
    }
})

// ── Environment ──────────────────────────────────────────────────────────────

private class PullEnv(
    currentWorkspaceId: String? = null,
    firebaseUid: String? = "uid-1",
    providerWorkspaceIds: List<String> = emptyList(),
    invitedWorkspaceIds: List<String> = emptyList(),
) {
    val applyOrder = mutableListOf<String>()
    val reader = FakeCollectionReader()
    val syncMeta = FakeSyncMetaRepository()

    val rootPlugin = FakePlugin(
        entityType = "WORKSPACE",
        collectionName = null,
        pullPriority = 0,
        applyOrder = applyOrder,
    )
    val accountsPlugin = FakePlugin(
        entityType = "ACCOUNT",
        collectionName = "accounts",
        pullPriority = 10,
        applyOrder = applyOrder,
    )
    val invitesPlugin = FakePlugin(
        entityType = "WORKSPACE_INVITE",
        collectionName = "invites",
        pullPriority = 5,
        applyOrder = applyOrder,
    )

    private val useCase = PullRemoteChangesUseCaseImpl(
        collectionReader = reader,
        syncMeta = syncMeta,
        plugins = listOf(accountsPlugin, invitesPlugin, rootPlugin),
        session = InMemorySessionPointers(
            currentWorkspaceId = currentWorkspaceId?.let(::WorkspaceId),
            currentFirebaseUid = firebaseUid,
        ),
        userWorkspacesProvider = FakeUserWorkspacesProvider(
            workspaceIds = providerWorkspaceIds,
            invitedIds = invitedWorkspaceIds,
        ),
    )

    suspend fun pull(
        scope: SyncScope,
        onProgress: suspend (PullProgress) -> Unit = {},
        cancelToken: SimpleCancelToken = SimpleCancelToken(),
    ) = useCase(scope, onProgress, cancelToken)
}

private fun doc(id: String, updatedAt: Long): RemoteDocument = FakeRemoteDocument(id, updatedAt)

private data class FetchCall(val workspaceId: String, val collection: String, val sinceMillis: Long)

private class FakeRemoteDocument(
    override val id: String,
    private val updatedAt: Long,
) : RemoteDocument {
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T =
        error("fake plugins never decode documents")

    override fun getLong(field: String): Long? = if (field == "updatedAt") updatedAt else null
}

private class FakeCollectionReader : WorkspaceCollectionReader {
    val rootDocs = mutableMapOf<String, RemoteDocument>()
    val collectionDocs = mutableMapOf<Pair<String, String>, List<RemoteDocument>>()
    val inviteDocs = mutableMapOf<Pair<String, String>, List<RemoteDocument>>()

    /** Workspaces whose every read raises, as Firestore rules would for a non-member. */
    val denyWorkspaces = mutableSetOf<String>()

    /** `workspaceId to collection` pairs that raise while the rest of the workspace reads fine. */
    val denyCollections = mutableSetOf<Pair<String, String>>()

    /** Workspaces whose reads fail with something *other* than a denial, keyed by message. */
    val failWorkspacesWith = mutableMapOf<String, String>()

    val fetchCalls = mutableListOf<FetchCall>()
    val inviteFetchCalls = mutableListOf<FetchCall>()

    override suspend fun fetchWorkspaceDoc(workspaceId: String): RemoteDocument? {
        denyIfBlocked(workspaceId, "root")
        return rootDocs[workspaceId]
    }

    override suspend fun fetchUpdatedSince(
        workspaceId: String,
        collectionName: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> {
        denyIfBlocked(workspaceId, collectionName)
        fetchCalls += FetchCall(workspaceId, collectionName, sinceMillis)
        return collectionDocs[workspaceId to collectionName].orEmpty()
            .filter { (it.getLong("updatedAt") ?: 0L) > sinceMillis }
            .take(limit)
    }

    private fun denyIfBlocked(workspaceId: String, collectionName: String) {
        failWorkspacesWith[workspaceId]?.let { error(it) }
        if (workspaceId in denyWorkspaces || (workspaceId to collectionName) in denyCollections) {
            error("PERMISSION_DENIED: Missing or insufficient permissions")
        }
    }

    override suspend fun fetchInvitesForUser(
        workspaceId: String,
        uid: String,
        sinceMillis: Long,
        limit: Int,
    ): List<RemoteDocument> {
        inviteFetchCalls += FetchCall(workspaceId, "invites", sinceMillis)
        return inviteDocs[workspaceId to uid].orEmpty()
            .filter { (it.getLong("updatedAt") ?: 0L) > sinceMillis }
            .take(limit)
    }
}

private class FakeSyncMetaRepository : SyncMetaRepository {
    val cursors = mutableMapOf<Pair<String, String>, Instant>()
    val attemptMarks = mutableListOf<Pair<String, String>>()
    val successMarks = mutableListOf<Pair<String, String>>()

    override suspend fun cursor(scopeKey: String, collection: String): Instant? =
        cursors[scopeKey to collection]

    override suspend fun setCursor(scopeKey: String, collection: String, cursor: Instant) {
        cursors[scopeKey to collection] = cursor
    }

    override suspend fun markAttempt(scopeKey: String, collection: String, at: Instant) {
        attemptMarks += scopeKey to collection
    }

    override suspend fun markSuccess(scopeKey: String, collection: String, at: Instant) {
        successMarks += scopeKey to collection
    }

    override suspend fun clearScope(scopeKey: String) {
        cursors.keys.removeAll { it.first == scopeKey }
    }

    override fun observe(scopeKey: String): Flow<List<SyncMeta>> = flowOf(emptyList())
}

private class FakeUserWorkspacesProvider(
    private val workspaceIds: List<String>,
    private val invitedIds: List<String>,
) : UserWorkspacesProvider {
    override suspend fun workspaceIds(): List<String> = workspaceIds
    override suspend fun invitedWorkspaceIds(): List<String> = invitedIds
}

private class FakePlugin(
    override val entityType: String,
    private val collectionName: String?,
    override val pullPriority: Int,
    private val applyOrder: MutableList<String>,
) : SyncEntityPlugin {
    override val firestoreCollectionName: String? get() = collectionName

    val applied = mutableListOf<Pair<String, String>>()
    val resultOverrides = mutableMapOf<String, EntityApplyResult>()
    var failOnDocId: String? = null
    var onApply: (String) -> Unit = {}

    override suspend fun push(mutation: PendingMutation) =
        error("push is not part of the pull path")

    override suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult {
        if (doc.id == failOnDocId) error("apply failed for ${doc.id}")
        applied += doc.id to scopeKey
        applyOrder += "$entityType:${doc.id}"
        onApply(doc.id)
        return resultOverrides[doc.id] ?: EntityApplyResult(applied = true, wasConflict = false)
    }
}
