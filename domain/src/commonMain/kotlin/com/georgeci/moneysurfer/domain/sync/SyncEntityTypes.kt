package com.georgeci.moneysurfer.domain.sync

/**
 * Opaque entity-type strings that flow through the sync outbox.
 * Values are stable — they are persisted in the local database as [PendingMutation.entityType].
 */
object SyncEntityTypes {
    const val USER = "USER"
    const val WORKSPACE = "WORKSPACE"
    const val WORKSPACE_MEMBER = "WORKSPACE_MEMBER"
    const val WORKSPACE_INVITE = "WORKSPACE_INVITE"
    const val WORKSPACE_REF = "WORKSPACE_REF"
    const val ACCOUNT = "ACCOUNT"
    const val CATEGORY = "CATEGORY"
    const val TRANSACTION = "TRANSACTION"
    const val BUDGET = "BUDGET"
    const val RECURRING_RULE = "RECURRING_RULE"
    const val GOAL = "GOAL"
    const val GOAL_CONTRIBUTION = "GOAL_CONTRIBUTION"

    /**
     * One synced setting. Unlike every other type here the entity id is not a UUID but the
     * configuration key name (`ui.theme_mode`), and `scopeKey` is null — settings belong to the
     * user, not to a workspace.
     */
    const val USER_CONFIG = "USER_CONFIG"
}
