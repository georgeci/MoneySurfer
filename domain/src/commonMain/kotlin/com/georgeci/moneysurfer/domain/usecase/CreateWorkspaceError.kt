package com.georgeci.moneysurfer.domain.usecase

/**
 * Typed failure for [CreateWorkspaceUseCase]. UI maps each variant to a user-facing
 * message. Cause-bearing variants keep the underlying [Throwable] so reporting can log it.
 */
sealed interface CreateWorkspaceError {
    /** No `currentUserId` in session — caller should not even be on the create screen. */
    data object NoCurrentUser : CreateWorkspaceError

    /** Local Room insert failed (disk full, FK violation, etc.). Workspace was NOT created. */
    data class LocalWriteFailed(val cause: Throwable) : CreateWorkspaceError

    /**
     * Remote `syncWorkspace` push failed (PERMISSION_DENIED, network, etc.).
     * The local Room rows ARE persisted, but the workspace is not pinned and not
     * registered under `users/{uid}.workspaceIds` — caller can let the user retry,
     * or surface the workspace via the selector for a later manual sync.
     */
    data class RemoteSyncFailed(val cause: Throwable) : CreateWorkspaceError
}
