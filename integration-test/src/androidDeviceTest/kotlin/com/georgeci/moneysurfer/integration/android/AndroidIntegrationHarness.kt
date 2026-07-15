package com.georgeci.moneysurfer.integration.android

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.repository.UserRemoteRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceInviteRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceMemberRepositoryImpl
import com.georgeci.moneysurfer.data.sync.FirebaseCurrentAuthInfo
import com.georgeci.moneysurfer.data.sync.FirebaseUserWorkspacesProvider
import com.georgeci.moneysurfer.data.sync.FirebaseWorkspaceCollectionReader
import com.georgeci.moneysurfer.data.sync.PullRemoteChangesUseCaseImpl
import com.georgeci.moneysurfer.data.sync.UploadPendingChangesUseCaseImpl
import com.georgeci.moneysurfer.data.sync.plugin.AccountSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.CategorySyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.TransactionSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceInviteSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceMemberSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.WorkspaceSyncPlugin
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.internal.repository.PendingMutationQueueImpl
import com.georgeci.moneysurfer.sync.internal.repository.SyncMetaRepositoryImpl
import com.georgeci.moneysurfer.sync.repository.ConflictResolver
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * One-stop wiring for an Android-device integration test. Spins up the real
 * production `:data` impls against:
 *  - in-memory Room (so the SQLite schema goes through the same DAOs as prod),
 *  - gitlive Firebase pointing at the local Emulator Suite (rules, indexes,
 *    auth all enforced),
 *  - in-memory `SessionPointers` (no DataStore plumbing — irrelevant for these
 *    tests).
 *
 * Each test owns its own harness — Room is in-memory and `FirebaseApp` is a
 * named per-instance app so emulator state stays isolated. Call [close] in
 * `@After` so the SQLite native handle is released and `EmulatorEnv` cleans
 * up its FirebaseApp.
 *
 * Not yet wired: full Workspace CRUD repos, higher-level domain use cases.
 * Add piece-by-piece as tests need them. Keeping the harness narrow keeps
 * the dependency surface (and the `androidDeviceTest` build time) sane.
 */
class AndroidIntegrationHarness(appName: String? = null) {
    val env = EmulatorEnv(appName = appName)

    val database: MoneySurferDatabase = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.inMemoryDatabaseBuilder(context, MoneySurferDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    val syncDatabase: SyncDatabase = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val pendingMutationQueue = PendingMutationQueueImpl(syncDatabase.pendingMutationDao())

    val session = InMemorySessionPointers()

    /** Fake gate — we don't exercise the version-gating logic in integration tests. */
    private val alwaysSupportedGate = object : AppVersionGate {
        override val status: StateFlow<AppVersionStatus?> =
            MutableStateFlow<AppVersionStatus?>(AppVersionStatus.Supported).asStateFlow()
        override suspend fun refresh(): AppVersionStatus = AppVersionStatus.Supported
        override fun isSyncAllowed(): Boolean = true
    }

    val outboxEnqueuer: OutboxEnqueuer = OutboxEnqueuerImpl(
        outbox = pendingMutationQueue,
        session = session,
        appVersionGate = alwaysSupportedGate,
    )

    val inviteRepository = WorkspaceInviteRepositoryImpl(
        dao = database.workspaceInviteDao(),
        outboxEnqueuer = outboxEnqueuer,
        clock = com.georgeci.moneysurfer.domain.primitives.ClockUseCase(),
        timeFormatter = com.georgeci.moneysurfer.data.repository.TimeFormatter(),
    )

    val memberRepository = WorkspaceMemberRepositoryImpl(
        dao = database.workspaceMemberDao(),
        outboxEnqueuer = outboxEnqueuer,
        clock = com.georgeci.moneysurfer.domain.primitives.ClockUseCase(),
        timeFormatter = com.georgeci.moneysurfer.data.repository.TimeFormatter(),
    )

    val userRemoteRepository = UserRemoteRepositoryImpl(env.firestore)

    val conflictResolver: ConflictResolver = LwwConflictResolver()

    val syncMetaRepository = SyncMetaRepositoryImpl(syncDatabase.syncMetaDao())

    private val appInfo = AppInfo(version = "test", versionCode = 1)

    private val collectionReader = FirebaseWorkspaceCollectionReader(env.firestore)

    private val userWorkspacesProvider = FirebaseUserWorkspacesProvider(
        firestore = env.firestore,
        session = session,
    )

    val syncPlugins = listOf(
        WorkspaceSyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            workspaceDao = database.workspaceDao(),
            userDao = database.userDao(),
        ),
        WorkspaceMemberSyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            conflictResolver = conflictResolver,
            workspaceMemberDao = database.workspaceMemberDao(),
            userDao = database.userDao(),
        ),
        WorkspaceInviteSyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            conflictResolver = conflictResolver,
            workspaceInviteDao = database.workspaceInviteDao(),
        ),
        AccountSyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            conflictResolver = conflictResolver,
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao(),
        ),
        CategorySyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            conflictResolver = conflictResolver,
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
        ),
        TransactionSyncPlugin(
            firestore = env.firestore,
            appInfo = appInfo,
            conflictResolver = conflictResolver,
            transactionDao = database.transactionDao(),
        ),
    )

    private val authInfo = FirebaseCurrentAuthInfo(env.auth)

    val uploadPendingChanges = UploadPendingChangesUseCaseImpl(
        outbox = pendingMutationQueue,
        authInfo = authInfo,
        plugins = syncPlugins,
    )

    val pullRemoteChanges = PullRemoteChangesUseCaseImpl(
        collectionReader = collectionReader,
        syncMeta = syncMetaRepository,
        plugins = syncPlugins,
        session = session,
        userWorkspacesProvider = userWorkspacesProvider,
    )

    fun close() {
        database.close()
        syncDatabase.close()
        env.delete()
    }
}
