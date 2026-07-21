package com.georgeci.moneysurfer.integration.android

/**
 * Every [com.georgeci.moneysurfer.data.sync.PullRemoteChangesUseCaseImpl] pull applies the
 * workspace **root** document unconditionally: [com.georgeci.moneysurfer.data.sync.plugin.WorkspaceSyncPlugin]
 * has `firestoreCollectionName == null`, so the pull fetches it via
 * `WorkspaceCollectionReader.fetchWorkspaceDoc` and applies it every time — it is NOT
 * cursor-gated like the subcollections. As long as the workspace doc exists in Firestore
 * and is not tombstoned, `applyDoc` upserts it and reports `applied = true`, contributing
 * exactly this many docs to `PullSummary.downloadedCount` on every pull (including a fully
 * caught-up re-pull, where the subcollections add nothing).
 *
 * The unit spec pins this behavior (see `PullRemoteChangesUseCaseImplSpec` —
 * "workspace root document is applied before subcollection plugins"), so the device tests
 * express their expected counts as `WORKSPACE_ROOT_DOCS_PER_PULL + <subcollection delta>`
 * rather than assuming an idle pull downloads zero.
 */
internal const val WORKSPACE_ROOT_DOCS_PER_PULL: Int = 1
