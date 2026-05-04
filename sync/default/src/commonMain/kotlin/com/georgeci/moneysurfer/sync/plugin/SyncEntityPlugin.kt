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

    suspend fun push(mutation: PendingMutation)
    suspend fun applyDoc(doc: RemoteDocument, scopeKey: String): EntityApplyResult
}

data class EntityApplyResult(val applied: Boolean, val wasConflict: Boolean)
