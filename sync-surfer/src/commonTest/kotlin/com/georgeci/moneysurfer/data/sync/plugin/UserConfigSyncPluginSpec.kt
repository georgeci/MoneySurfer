package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import com.georgeci.moneysurfer.data.remote.UserConfigDoc
import com.georgeci.moneysurfer.data.sync.UserConfigRemoteSource
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.PullScope
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

private const val THEME = "ui.theme_mode"

class UserConfigSyncPluginSpec : StringSpec({

    "it is user-scoped, so the workspace loop never touches it" {
        env().plugin.pullScope shouldBe PullScope.User
    }

    // ── push ──────────────────────────────────────────────────────────────────

    "push sends the row as it stands now and records it as pushed" {
        // Outbox rows carry no payload: whatever the key holds at drain time is what goes.
        val env = env(versionCode = 42)
        env.dao.write(key = THEME, value = "Dark", updatedAt = 100)

        env.plugin.push(upsertOf(THEME))

        env.remote.writes shouldContainExactly listOf(
            RemoteWrite("uid-1", THEME, UserConfigDoc(value = "Dark", updatedAt = 100, clientVersionCode = 42)),
        )
        env.dao.getByKey(THEME)?.lastPushedAt shouldBe 100
    }

    "a value written after the push read it stays pending" {
        // `markPushed` is scoped to the `updatedAt` that was actually sent, so the newer local
        // value is not silently marked as delivered.
        val env = env()
        env.dao.write(key = THEME, value = "Dark", updatedAt = 100)
        env.remote.onWrite = { env.dao.write(key = THEME, value = "Light", updatedAt = 200) }

        env.plugin.push(upsertOf(THEME))

        env.dao.getByKey(THEME)?.lastPushedAt.shouldBeNull()
        env.dao.keysPendingPush() shouldContainExactly listOf(THEME)
    }

    "a key whose row the account wipe removed is not resurrected on the server" {
        // Logout drops `config_entry` while outbox rows for it may still be queued.
        val env = env()

        env.plugin.push(upsertOf(THEME))

        env.remote.writes.shouldBeEmpty()
    }

    "push without a Firebase session throws, so the drain requeues instead of dropping the row" {
        // `UploadPendingChangesUseCaseImpl` treats a push that returns normally as delivered and
        // deletes the outbox row. Returning quietly here would throw away the queue entry for a
        // setting that never reached the server; throwing routes it to `markFailed` → `PENDING`.
        val env = env(firebaseUid = null)
        env.dao.write(key = THEME, value = "Dark", updatedAt = 100)

        shouldThrow<IllegalStateException> { env.plugin.push(upsertOf(THEME)) }

        env.remote.writes.shouldBeEmpty()
        env.dao.keysPendingPush() shouldContainExactly listOf(THEME)
    }

    "a DELETE is ignored — settings are overwritten, never deleted" {
        val env = env()
        env.dao.write(key = THEME, value = "Dark", updatedAt = 100)

        env.plugin.push(upsertOf(THEME).copy(operation = MutationOperation.DELETE))

        env.remote.writes.shouldBeEmpty()
    }

    // ── pull ──────────────────────────────────────────────────────────────────

    "a newer remote value is adopted and stamped as already pushed" {
        val env = env()
        env.dao.write(key = THEME, value = "Light", updatedAt = 100)

        val result = env.plugin.applyDoc(docOf(THEME, value = "Dark", updatedAt = 200), scopeKey = "uid-1")

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        env.dao.getByKey(THEME)?.value shouldBe "Dark"
        // Not re-pushed on the next reconciliation: this value came from the server.
        env.dao.keysPendingPush().shouldBeEmpty()
    }

    "a key this device has never seen is taken" {
        val env = env()

        env.plugin.applyDoc(docOf(THEME, value = "Dark", updatedAt = 1), scopeKey = "uid-1")

        env.dao.getByKey(THEME)?.value shouldBe "Dark"
    }

    "an older remote value loses and the local one is left alone" {
        val env = env()
        env.dao.write(key = THEME, value = "Light", updatedAt = 300)

        val result = env.plugin.applyDoc(docOf(THEME, value = "Dark", updatedAt = 200), scopeKey = "uid-1")

        result.applied shouldBe false
        env.dao.getByKey(THEME)?.value shouldBe "Light"
    }

    "re-reading an unchanged document is not reported as a conflict" {
        // The user-scoped phase has no cursor, so every pull re-reads every document. Counting each
        // of those as a conflict would make the sync summary meaningless.
        val env = env()
        env.dao.applyRemote(key = THEME, value = "Dark", updatedAt = 200)

        val result = env.plugin.applyDoc(docOf(THEME, value = "Dark", updatedAt = 200), scopeKey = "uid-1")

        result shouldBe EntityApplyResult(applied = false, wasConflict = false)
    }

    "an undecodable document is skipped instead of taking the pull down" {
        // Poison-document guard (issue #156): these documents are client-written, and the pull path
        // reads `updatedAt`.
        val env = env()

        val result = env.plugin.applyDoc(UndecodableDocument(THEME), scopeKey = "uid-1")

        result shouldBe EntityApplyResult(applied = false, wasConflict = false)
        env.dao.getAll().shouldBeEmpty()
    }

    "a key this build does not know is stored rather than dropped" {
        // A mixed-version pair of devices must not lose each other's settings.
        val env = env()

        env.plugin.applyDoc(docOf("ui.future_key", value = "x", updatedAt = 1), scopeKey = "uid-1")

        env.dao.getByKey("ui.future_key")?.value shouldBe "x"
    }
})

// ── Environment ──────────────────────────────────────────────────────────────

private class PluginEnv(firebaseUid: String?, versionCode: Int) {
    val dao = FakeConfigEntryDao()
    val remote = RecordingRemote()
    val plugin = UserConfigSyncPlugin(
        remote = remote,
        appInfo = AppInfo(version = "test", versionCode = versionCode),
        conflictResolver = LwwConflictResolver(),
        configEntryDao = dao,
        session = InMemorySessionPointers(currentFirebaseUid = firebaseUid),
    )
}

private fun env(firebaseUid: String? = "uid-1", versionCode: Int = 1) = PluginEnv(firebaseUid, versionCode)

private fun upsertOf(key: String): PendingMutation = PendingMutation(
    id = "m-1",
    entityType = "USER_CONFIG",
    entityId = key,
    operation = MutationOperation.UPDATE,
    scopeKey = null,
    createdAt = Clock.System.now(),
    attempts = 0,
    lastError = null,
)

private data class RemoteWrite(val uid: String, val key: String, val doc: UserConfigDoc)

private class RecordingRemote : UserConfigRemoteSource {
    val writes = mutableListOf<RemoteWrite>()

    /** Models a local write racing the push: it runs while the document is in flight. */
    var onWrite: suspend () -> Unit = {}

    override suspend fun write(uid: String, key: String, doc: UserConfigDoc) {
        writes += RemoteWrite(uid, key, doc)
        onWrite()
    }
}

/** Decodes through real kotlinx-serialization, so a DTO change is caught here too. */
private class JsonRemoteDocument(
    override val id: String,
    private val fields: Map<String, Any>,
) : RemoteDocument {

    override fun <T> decode(deserializer: DeserializationStrategy<T>): T = Json.decodeFromJsonElement(
        deserializer,
        JsonObject(
            fields.mapValues { (_, value) ->
                when (value) {
                    is Long -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                }
            },
        ),
    )

    override fun getLong(field: String): Long? = fields[field] as? Long
}

private fun docOf(id: String, value: String, updatedAt: Long): RemoteDocument = JsonRemoteDocument(
    id = id,
    fields = mapOf("value" to value, "updatedAt" to updatedAt, "clientVersionCode" to 1),
)

/** What a wrong-typed field looks like to the plugin: `decode` throws. */
private class UndecodableDocument(override val id: String) : RemoteDocument {
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T =
        error("value: {} is not a string")

    override fun getLong(field: String): Long? = null
}

/** In-memory `config_entry` with the same column semantics as the real statements. */
private class FakeConfigEntryDao : ConfigEntryDao {
    private val rows = MutableStateFlow<Map<String, ConfigEntryEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ConfigEntryEntity>> = rows.map { it.values.toList() }

    override suspend fun getAll(): List<ConfigEntryEntity> = rows.value.values.toList()

    override suspend fun getByKey(key: String): ConfigEntryEntity? = rows.value[key]

    override suspend fun write(key: String, value: String, updatedAt: Long) {
        val existing = rows.value[key]
        rows.value = rows.value + (
            key to ConfigEntryEntity(key, value, updatedAt, existing?.lastPushedAt)
            )
    }

    override suspend fun applyRemote(key: String, value: String, updatedAt: Long) {
        rows.value = rows.value + (key to ConfigEntryEntity(key, value, updatedAt, updatedAt))
    }

    override suspend fun markPushed(key: String, pushedUpdatedAt: Long) {
        val row = rows.value[key] ?: return
        if (row.updatedAt != pushedUpdatedAt) return
        rows.value = rows.value + (key to row.copy(lastPushedAt = pushedUpdatedAt))
    }

    override suspend fun keysPendingPush(): List<String> = rows.value.values
        .filter { row -> row.lastPushedAt?.let { row.updatedAt > it } ?: true }
        .map { it.key }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}
