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
    const val WORKSPACE_MEMBERS = "members"
    const val WORKSPACE_INVITES = "invites"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val RECURRING_RULES = "recurringRules"
    const val GOALS = "goals"
    const val GOAL_CONTRIBUTIONS = "goalContributions"
}
