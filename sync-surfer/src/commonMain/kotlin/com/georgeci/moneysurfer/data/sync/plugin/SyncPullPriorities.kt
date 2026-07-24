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
 *  35  recurringRules — FK: workspaceId, categoryId  ← must be > categories, and < transactions
 *                    so a pulled transaction's recurringRuleId resolves to a rule this device
 *                    already has instead of dangling (issue #269)
 *  40  transactions — FK: accountId, categoryId  ← must be > accounts and categories
 *  50  budgets     — FK: workspaceId; references category ids in a CSV column (no Room FK),
 *                    so running after categories is ordering hygiene, not a hard constraint
 *  60  goals       — FK: workspaceId, accountId (nullable)  ← must be > accounts
 *  70  goalContributions — FK: workspaceId, goalId (cascade)  ← must be > goals
 * 100  (default)   — no ordering constraint
 */
internal object SyncPullPriorities {
    const val WORKSPACE = -100
    const val MEMBERS = 0
    const val INVITES = 10
    const val ACCOUNTS = 20
    const val CATEGORIES = 30
    const val RECURRING_RULES = 35
    const val TRANSACTIONS = 40
    const val BUDGETS = 50
    const val GOALS = 60
    const val GOAL_CONTRIBUTIONS = 70
    const val DEFAULT = 100
}
