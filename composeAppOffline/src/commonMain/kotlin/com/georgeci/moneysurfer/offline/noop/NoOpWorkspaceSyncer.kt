package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer

/**
 * Offline build has no remote sync. Every push/pull request is a silent
 * success — the on-disk Room database is the source of truth.
 */
class NoOpWorkspaceSyncer : WorkspaceSyncer {
    /** Nothing is ever pushed in the offline build, and callers must be able to tell. */
    override suspend fun pushAll(): Boolean = false
    override suspend fun syncAll() = Unit
    override suspend fun syncWorkspace(workspaceId: WorkspaceId) = Unit
}
