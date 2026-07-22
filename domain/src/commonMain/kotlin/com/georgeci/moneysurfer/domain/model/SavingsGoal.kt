package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A savings goal — a counter of intent, not a pot of money.
 *
 * The amount saved so far is never stored here: it is always derived as
 * `SUM(GoalContribution.amount)` for the goal, so there is no field that can
 * drift away from the contribution history.
 *
 * [accountId] records where the user intends the money to sit. It is
 * decorative — nothing verifies that the account actually holds it, and no
 * account balance changes when a contribution is recorded.
 */
data class SavingsGoal(
    val id: GoalId = GoalId.uuid(),
    val workspaceId: WorkspaceId,
    val title: String,
    val emoji: String,
    /** Hue in `0..360`, tints the goal's icon tile. */
    val hue: Int,
    val target: Money,
    val currencyCode: CurrencyCode,
    val startDate: LocalDate,
    /** `null` when the goal has no deadline. */
    val targetDate: LocalDate? = null,
    val accountId: AccountId? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    PAUSED,
    ARCHIVED,
}
