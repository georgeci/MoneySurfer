package com.georgeci.moneysurfer.sync.plugin

import com.georgeci.moneysurfer.sync.repository.PendingMutation

/**
 * Self-contained per-entity sync adapter.
 *
 * [UploadPendingChangesUseCaseImpl] calls [push] for mutations whose
 * [entityType] matches. [PullRemoteChangesUseCaseImpl] calls [applyDoc]
 * for each document in the plugin's [firestoreCollectionName].
 *
 * [firestoreCollectionName] is null for entities that are not pulled via the
 * subcollection cursor path (e.g. workspace root docs, users).
 *
 * [pullPriority] controls the pull order within a workspace. Lower values run
 * first. Plugins that have Room FK dependencies on other plugins must declare a
 * higher (later) priority than the plugins they depend on. Defaults to 0
 * (no ordering preference). Concrete values are defined in each plugin
 * implementation inside `:sync-surfer`.
 */
interface SyncEntityPlugin {
    val entityType: String
    val firestoreCollectionName: String?

    /** Pull order within a workspace. Lower = pulled first. Default: 100. */
    val pullPriority: Int get() = 100

    /**
     * Which document tree this plugin's collection hangs off. Defaults to
     * [PullScope.Workspace] — everything but per-user configuration.
     */
    val pullScope: PullScope get() = PullScope.Workspace

    suspend fun push(mutation: PendingMutation)

    /**
     * Applies one remote document. [scopeKey] is the workspace id for a
     * [PullScope.Workspace] plugin and the Firebase uid for a [PullScope.User] one — the
     * same value the plugin's own outbox rows carry in `scopeKey`.
     */
    suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult
}

/**
 * Whether a plugin's documents live under `workspaces/{wid}` or under `users/{uid}`.
 *
 * The discriminator exists because [PullRemoteChangesUseCaseImpl] runs *every* registered plugin
 * for *every* workspace. A user-scoped plugin dragged into that loop breaks either way: with
 * `firestoreCollectionName = null` it is handed the workspace root document and a lenient DTO
 * writes a garbage row, and with a name it queries a path that does not exist, once per workspace.
 */
enum class PullScope {
    Workspace,
    User,
}

data class EntityApplyResult(val applied: Boolean, val wasConflict: Boolean)
