package com.georgeci.moneysurfer.sync.api

/**
 * Canonical Firestore sub-collection name constants.
 *
 * These are the string keys used as the `collection` parameter in
 * [com.georgeci.moneysurfer.sync.repository.SyncMetaRepository] calls and as
 * [com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin.firestoreCollectionName]
 * values in the sync plugins. Keeping them in one place avoids magic strings
 * scattered across tests and production code.
 */
object SyncCollection {

    /**
     * Root collection of user documents. Not a sub-collection like the rest, but it is the parent
     * of [USER_CONFIG], and every path built from the two belongs in one place — the reader, the
     * writer and the account-deletion purge all have to agree on it or a rename goes silently
     * half-applied.
     */
    const val USERS = "users"

    /**
     * Root collection of workspace documents — the parent every constant below hangs off. Same
     * reasoning as [USERS]: the reader and the writer both build paths from it.
     */
    const val WORKSPACES = "workspaces"

    const val WORKSPACE_MEMBERS = "members"
    const val WORKSPACE_INVITES = "invites"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val RECURRING_RULES = "recurringRules"
    const val GOALS = "goals"
    const val GOAL_CONTRIBUTIONS = "goalContributions"

    /**
     * `users/{uid}/config` — the one collection here that hangs off the *user* document rather
     * than a workspace. Read by the user-scoped pull phase, one document per settings key.
     */
    const val USER_CONFIG = "config"
}
