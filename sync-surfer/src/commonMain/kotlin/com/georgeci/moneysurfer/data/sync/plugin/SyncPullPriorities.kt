package com.georgeci.moneysurfer.data.sync.plugin

/**
 * Pull-order priorities for workspace sync plugins.
 *
 * Lower values run first. Gaps of 10 leave room for future plugins.
 * Order is driven by Room FK dependencies — a plugin must run after all
 * plugins whose entities it references as a foreign key.
 *
 *   0  members     — creates UserEntity stubs (insertIgnore); no FK deps
 *  10  invites     — FK: workspaceId (no Room FK enforced, but logically after members)
 *  20  accounts    — FK: workspaceId
 *  30  categories  — FK: workspaceId, parentId (self)
 *  40  transactions — FK: accountId, categoryId  ← must be > accounts and categories
 * 100  (default)   — no ordering constraint
 */
internal object SyncPullPriorities {
    const val WORKSPACE = -100
    const val MEMBERS = 0
    const val INVITES = 10
    const val ACCOUNTS = 20
    const val CATEGORIES = 30
    const val TRANSACTIONS = 40
    const val DEFAULT = 100
}
