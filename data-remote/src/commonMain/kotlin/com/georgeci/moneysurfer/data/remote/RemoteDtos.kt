package com.georgeci.moneysurfer.data.remote

import kotlinx.serialization.Serializable

/**
 * Firestore wire-format DTOs. Field names mirror Room entities 1:1 so a Firestore document
 * can round-trip through Kotlin serialization without a custom mapping layer.
 */

@Serializable
data class UserDoc(
    val displayName: String? = null,
    val email: String? = null,
    val isAnon: Boolean = false,
    val createdAt: Long = 0L,
    val workspaceIds: List<String> = emptyList(),
    val defaultWorkspaceId: String? = null,
    val invitedWorkspaceIds: List<String> = emptyList(),
)

/**
 * Email→uid mapping for invite recipient discovery. Doc id is the lowercased email.
 * Kept as a separate collection (rather than exposing `users.email` via list rules) so the
 * security rule is a tight `signedIn()` read on a single field with no other PII leakage.
 */
@Serializable
data class UserEmailDoc(
    val uid: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
data class WorkspaceDoc(
    val name: String = "",
    val description: String = "",
    val baseCurrency: String = "",
    val ownerId: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

@Serializable
data class AccountDoc(
    val name: String = "",
    val type: String = "",
    val currency: String = "",
    val balance: Long = 0L,
    val archived: Boolean = false,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
    /**
     * When the account was archived. Null both for active accounts and for rows written by a
     * client that predates the field — the manage list falls back to a dateless label either way.
     */
    val archivedAt: Long? = null,
    /**
     * User-entered key–value details. Empty when written by a client that predates the field;
     * the reader treats that as "no details" rather than refusing the document.
     */
    val extraDetails: List<AccountExtraDetailDoc> = emptyList(),
    /**
     * Position in the workspace's account list, ascending. Zero both for the first account and
     * for rows written by a client that predates the field — the reader breaks ties by name, so
     * an un-migrated workspace still lists in a stable order.
     */
    val sortOrder: Int = 0,
)

/**
 * One "Extra details" entry. [key] is either a well-known key name (`IBAN`, `CARD_LAST4`, …) or
 * a label the user typed — see `AccountExtraDetail` in the domain.
 */
@Serializable
data class AccountExtraDetailDoc(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class CategoryDoc(
    val name: String = "",
    val type: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
    /** System kind marker (e.g. "TRANSFER") for built-in categories. Null for user categories. */
    val systemKind: String? = null,
    /**
     * Semantic icon key. Empty when written by a client that predates the field; the reader
     * resolves that back to the deterministic default rather than rendering nothing.
     */
    val iconKey: String = "",
    /** Hue in degrees, 0 until 360. `-1` is the same "not written" sentinel as a blank [iconKey]. */
    val hue: Int = -1,
)

@Serializable
data class TransactionDoc(
    val accountId: String = "",
    val categoryId: String? = null,
    val amount: Long = 0L,
    val currencyCode: String = "",
    val note: String = "",
    val merchant: String = "",
    /**
     * A real list on the wire even though Room stores it as a CSV column — same split as
     * [BudgetDoc.categoryIds], and the mapper converts on the entity↔wire boundary.
     */
    val tags: List<String> = emptyList(),
    val operationAt: Long = 0L,
    val operationDate: String = "",
    val type: String = "",
    val status: String = "ACTUAL",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
    /** Shared id linking the two legs of a transfer. Null for non-transfer rows. */
    val transferId: String? = null,
    /** Rule that generated this row. Null for manual entries. */
    val recurringRuleId: String? = null,
)

/**
 * Budget doc lives at `workspaces/{wid}/budgets/{bid}`.
 *
 * `categoryIds` is a real list on the wire even though Room stores it as a CSV column —
 * a list is the natural Firestore shape and the mapper converts. An empty list means
 * "every expense category".
 */
@Serializable
data class BudgetDoc(
    val name: String = "",
    val categoryIds: List<String> = emptyList(),
    val amount: Long = 0L,
    val period: String = "",
    /** ISO-8601 local date (`yyyy-MM-dd`) the period anchoring counts from. */
    val startDate: String = "",
    val alertPercent: Int = 0,
    val isActive: Boolean = true,
    val rollover: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Recurring-rule doc at `workspaces/{wid}/recurringRules/{rid}`.
 *
 * The rule must replicate because [TransactionDoc.recurringRuleId] points at it: on a device
 * that never created the rule the link would otherwise dangle and the cadence could not be
 * rendered (issue #269).
 *
 * The schedule is flattened to match the Room columns one-to-one, except that
 * `daysOfWeek` / `daysOfMonth` are real lists on the wire where Room keeps CSV columns —
 * the same entity↔wire conversion [BudgetDoc.categoryIds] makes.
 */
@Serializable
data class RecurringRuleDoc(
    val title: String = "",
    val amount: Long = 0L,
    val categoryId: String = "",
    val scheduleFrequency: String = "",
    val scheduleInterval: Int = 1,
    /** [kotlinx.datetime.DayOfWeek] names, e.g. `["MONDAY"]`. Empty for non-weekly schedules. */
    val scheduleDaysOfWeek: List<String> = emptyList(),
    /** Days of month, 1..31. Empty for non-monthly schedules. */
    val scheduleDaysOfMonth: List<Int> = emptyList(),
    val scheduleMissingDayPolicy: String = "",
    /** ISO-8601 local date (`yyyy-MM-dd`) the schedule anchors from. */
    val startDate: String = "",
    val nextRunAt: Long? = null,
    val autoCreate: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Savings goal doc at `workspaces/{wid}/goals/{gid}`.
 *
 * There is no `current` / `saved` field by design — the amount saved is always
 * `SUM(goalContributions.amount)` for the goal, so no wire field can drift.
 */
@Serializable
data class GoalDoc(
    val title: String = "",
    val emoji: String = "",
    val hue: Int = 0,
    val target: Long = 0L,
    val currencyCode: String = "",
    val startDate: String = "",
    val targetDate: String? = null,
    val accountId: String? = null,
    val status: String = "ACTIVE",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Contribution doc at `workspaces/{wid}/goalContributions/{cid}`.
 *
 * A flat workspace-level collection rather than a sub-collection of the goal:
 * the pull path reads workspace sub-collections by cursor, and nesting a level
 * deeper would need a collection-group query and a second cursor.
 */
@Serializable
data class GoalContributionDoc(
    val goalId: String = "",
    /** Signed minor units — negative is a withdrawal. */
    val amount: Long = 0L,
    val occurredOn: String = "",
    val note: String = "",
    /** Always null in v1 — reserved for the money-backed model. */
    val transferId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)

/**
 * Member doc lives at `workspaces/{wid}/members/{uid}`. Doc id = userId.
 *
 * Soft-delete via `status` (`ACTIVE` / `LEFT` / `REMOVED`) — `deletedAt` is kept for
 * back-compat with old docs and as a tombstone fallback for clients pulling members.
 */
@Serializable
data class WorkspaceMemberDoc(
    val userId: String = "",
    val role: String = "",
    val status: String = "ACTIVE",
    val displayName: String = "",
    val email: String? = null,
    val addedByUserId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val leftAt: Long? = null,
    val removedAt: Long? = null,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
    // Id of the owner-issued invite this member row was created from. Firestore rules
    // require it on self-create so they can verify an invite in this workspace admits
    // the caller (tenant-isolation gate, issue #152). Stamped by WorkspaceMemberSyncPlugin
    // at push time from the local invite; null for owner-created rows (owner path in the
    // rules does not need it) and absent on legacy docs.
    val inviteId: String? = null,
)

/**
 * Invite doc lives at `workspaces/{wid}/invites/{inviteId}`. Doc id = invite UUID.
 */
@Serializable
data class WorkspaceInviteDoc(
    val email: String = "",
    val targetUserId: String? = null,
    val role: String = "",
    val status: String = "PENDING",
    val invitedByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val respondedAt: Long? = null,
    val deletedAt: Long? = null,
    val clientVersionCode: Int = 1,
)
